package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.ReplaceRoleCapabilityIntentRequest;
import com.triobase.service.auth.dto.RoleAuthorizationDraftResponse;
import com.triobase.service.auth.entity.RoleAuthorizationDraftStatus;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCapabilityDependency;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.entity.SysRoleAuthDraft;
import com.triobase.service.auth.entity.SysRoleAuthIntent;
import com.triobase.service.auth.entity.SysRoleAuthRelease;
import com.triobase.service.auth.entity.SysRoleAuthActiveRelease;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityDependencyMapper;
import com.triobase.service.auth.mapper.AuthPageCatalogMapper;
import com.triobase.service.auth.mapper.RoleAuthDraftMapper;
import com.triobase.service.auth.mapper.RoleAuthIntentMapper;
import com.triobase.service.auth.mapper.RoleAuthReleaseMapper;
import com.triobase.service.auth.mapper.RoleAuthActiveReleaseMapper;
import com.triobase.service.auth.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RolePageCapabilityStore {

    private static final Set<String> EDITABLE_STATUSES = Set.of(
            RoleAuthorizationDraftStatus.DRAFT.name(),
            RoleAuthorizationDraftStatus.VALIDATED.name(),
            RoleAuthorizationDraftStatus.FAILED.name());
    private static final Set<String> SCOPE_TYPES = Set.of(
            "NONE", "SELF", "OWN_ORG", "OWN_ORG_AND_CHILDREN", "ASSIGNED_ORGS", "ALL");

    private final RoleAuthDraftMapper draftMapper;
    private final RoleAuthIntentMapper intentMapper;
    private final RoleAuthReleaseMapper releaseMapper;
    private final RoleAuthActiveReleaseMapper activeReleaseMapper;
    private final AuthPageCatalogMapper catalogMapper;
    private final AuthPageCapabilityMapper capabilityMapper;
    private final AuthPageCapabilityDependencyMapper dependencyMapper;
    private final RoleMapper roleMapper;
    private final AuthorizationRegistryService authorizationRegistryService;
    private final PageCapabilityManifestMaterializer manifestMaterializer;
    private final RoleAuthorizationAuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SysRoleAuthDraft getOrCreateDraft(String requestedTenantId, String roleId) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        requireRole(tenantId, roleId);

        SysRoleAuthDraft existing = draftMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthDraft>()
                .eq(SysRoleAuthDraft::getTenantId, tenantId)
                .eq(SysRoleAuthDraft::getRoleId, roleId)
                .in(SysRoleAuthDraft::getDraftStatus, EDITABLE_STATUSES));
        if (existing != null) {
            initializeLegacyEmptyDraft(existing);
            SysRoleAuthRelease activeRelease = latestRelease(tenantId, roleId);
            if (activeRelease != null
                    && !activeRelease.getId().equals(existing.getBasedReleaseId())) {
                rebaseEditableDraft(tenantId, roleId, activeRelease.getId());
                return requireDraft(tenantId, existing.getId());
            }
            return existing;
        }

        SysAuthPageCatalog catalog = findActiveCatalog(tenantId);
        if (catalog == null) {
            manifestMaterializer.materializeAndActivateTenant(tenantId);
            catalog = findActiveCatalog(tenantId);
        }
        if (catalog == null) {
            throw new BizException(40991, "PAGE_CAPABILITY_ACTIVE_CATALOG_REQUIRED");
        }

        SysRoleAuthDraft draft = new SysRoleAuthDraft();
        draft.setTenantId(tenantId);
        draft.setRoleId(roleId);
        draft.setCatalogId(catalog.getId());
        SysRoleAuthRelease latestRelease = latestRelease(tenantId, roleId);
        draft.setBasedReleaseId(latestRelease == null ? null : latestRelease.getId());
        draft.setDraftStatus(RoleAuthorizationDraftStatus.DRAFT.name());
        draft.setIntentVersion(1L);
        draft.setCreatedBy(currentActor());
        draft.setUpdatedBy(currentActor());
        draftMapper.insert(draft);
        int inheritedCount = inheritLatestRelease(draft, latestRelease);
        auditService.record(tenantId, roleId, draft.getId(), null,
                "DRAFT_CREATED",
                latestRelease == null ? "已为角色创建空白页面权限草稿" : "已基于最新发布版本创建增量草稿",
                Map.of("catalogId", catalog.getId(),
                        "basedReleaseId", latestRelease == null ? "" : latestRelease.getId(),
                        "inheritedSelectionCount", inheritedCount));
        return draft;
    }

    private void initializeLegacyEmptyDraft(SysRoleAuthDraft draft) {
        if (StringUtils.hasText(draft.getBasedReleaseId())) {
            return;
        }
        Long intentCount = intentMapper.selectCount(new LambdaQueryWrapper<SysRoleAuthIntent>()
                .eq(SysRoleAuthIntent::getTenantId, draft.getTenantId())
                .eq(SysRoleAuthIntent::getDraftId, draft.getId()));
        if (intentCount != null && intentCount > 0) {
            return;
        }
        SysRoleAuthRelease latestRelease = latestRelease(draft.getTenantId(), draft.getRoleId());
        if (latestRelease == null) {
            return;
        }
        draft.setBasedReleaseId(latestRelease.getId());
        draft.setUpdatedBy(currentActor());
        draftMapper.updateById(draft);
        int inheritedCount = inheritLatestRelease(draft, latestRelease);
        auditService.record(draft.getTenantId(), draft.getRoleId(), draft.getId(), latestRelease.getId(),
                "DRAFT_REBASED", "旧空白草稿已基于最新发布版本转换为增量草稿",
                Map.of("inheritedSelectionCount", inheritedCount));
    }

    private SysRoleAuthRelease latestRelease(String tenantId, String roleId) {
        SysRoleAuthActiveRelease active = activeReleaseMapper.selectOne(
                new LambdaQueryWrapper<SysRoleAuthActiveRelease>()
                        .eq(SysRoleAuthActiveRelease::getTenantId, tenantId)
                        .eq(SysRoleAuthActiveRelease::getRoleId, roleId));
        if (active != null && StringUtils.hasText(active.getReleaseId())) {
            return releaseMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthRelease>()
                    .eq(SysRoleAuthRelease::getTenantId, tenantId)
                    .eq(SysRoleAuthRelease::getRoleId, roleId)
                    .eq(SysRoleAuthRelease::getId, active.getReleaseId()));
        }
        return releaseMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthRelease>()
                .eq(SysRoleAuthRelease::getTenantId, tenantId)
                .eq(SysRoleAuthRelease::getRoleId, roleId)
                .orderByDesc(SysRoleAuthRelease::getReleaseNumber)
                .last("LIMIT 1"));
    }

    @Transactional
    public void rebaseEditableDraft(String requestedTenantId, String roleId, String releaseId) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        SysRoleAuthRelease release = releaseMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthRelease>()
                .eq(SysRoleAuthRelease::getTenantId, tenantId)
                .eq(SysRoleAuthRelease::getRoleId, roleId)
                .eq(SysRoleAuthRelease::getId, releaseId));
        if (release == null) {
            throw new BizException(40496, "ROLE_AUTH_RELEASE_NOT_FOUND");
        }
        SysRoleAuthDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthDraft>()
                .eq(SysRoleAuthDraft::getTenantId, tenantId)
                .eq(SysRoleAuthDraft::getRoleId, roleId)
                .in(SysRoleAuthDraft::getDraftStatus, EDITABLE_STATUSES));
        if (draft == null) {
            return;
        }
        intentMapper.delete(new LambdaQueryWrapper<SysRoleAuthIntent>()
                .eq(SysRoleAuthIntent::getTenantId, tenantId)
                .eq(SysRoleAuthIntent::getDraftId, draft.getId()));
        SysAuthPageCatalog activeCatalog = findActiveCatalog(tenantId);
        draft.setCatalogId(activeCatalog == null ? release.getCatalogId() : activeCatalog.getId());
        draft.setBasedReleaseId(releaseId);
        draft.setDraftStatus(RoleAuthorizationDraftStatus.DRAFT.name());
        draft.setIntentVersion((draft.getIntentVersion() == null ? 0L : draft.getIntentVersion()) + 1L);
        draft.setValidationTokenHash(null);
        draft.setValidationPlanHash(null);
        draft.setValidatedBy(null);
        draft.setValidatedAt(null);
        draft.setValidationExpiresAt(null);
        draft.setValidationSummary(null);
        draft.setUpdatedBy(currentActor());
        draftMapper.updateById(draft);
        int inheritedCount = inheritLatestRelease(draft, release);
        auditService.record(tenantId, roleId, draft.getId(), releaseId,
                "DRAFT_REBASED_AFTER_ROLLBACK", "编辑草稿已同步到恢复后的线上版本",
                Map.of("inheritedSelectionCount", inheritedCount));
    }

    private int inheritLatestRelease(SysRoleAuthDraft draft, SysRoleAuthRelease release) {
        if (release == null || !StringUtils.hasText(release.getIntentSnapshot())) {
            return 0;
        }
        List<SysRoleAuthIntent> releasedIntents;
        try {
            releasedIntents = objectMapper.readValue(release.getIntentSnapshot(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SysRoleAuthIntent.class));
        } catch (JsonProcessingException exception) {
            throw new BizException(40996, "ROLE_AUTH_RELEASE_SNAPSHOT_INVALID");
        }

        Map<String, String> capabilityMapping = capabilityMapping(
                draft.getTenantId(), release.getCatalogId(), draft.getCatalogId(), releasedIntents);
        int inherited = 0;
        for (SysRoleAuthIntent released : releasedIntents) {
            String targetCapabilityId = capabilityMapping.get(released.getCapabilityId());
            if (!StringUtils.hasText(targetCapabilityId)) {
                continue;
            }
            SysRoleAuthIntent intent = new SysRoleAuthIntent();
            intent.setTenantId(draft.getTenantId());
            intent.setDraftId(draft.getId());
            intent.setCapabilityId(targetCapabilityId);
            intent.setSelectionSource(released.getSelectionSource());
            intent.setDefaultScopeType(released.getDefaultScopeType());
            intent.setDefaultScopeIds(released.getDefaultScopeIds());
            intent.setOperationScopeType(released.getOperationScopeType());
            intent.setOperationScopeIds(released.getOperationScopeIds());
            intent.setFieldIntentJson(released.getFieldIntentJson());
            intent.setConstraintIntentJson(released.getConstraintIntentJson());
            intent.setCreatedBy(currentActor());
            intent.setUpdatedBy(currentActor());
            intentMapper.insert(intent);
            inherited++;
        }
        return inherited;
    }

    private Map<String, String> capabilityMapping(String tenantId, String sourceCatalogId,
                                                   String targetCatalogId,
                                                   List<SysRoleAuthIntent> intents) {
        Map<String, String> mapping = new LinkedHashMap<>();
        Set<String> sourceIds = intents.stream().map(SysRoleAuthIntent::getCapabilityId).collect(java.util.stream.Collectors.toSet());
        if (sourceCatalogId.equals(targetCatalogId)) {
            sourceIds.forEach(id -> mapping.put(id, id));
            return mapping;
        }
        Map<String, String> sourceCodeById = new LinkedHashMap<>();
        capabilityMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapability>()
                        .eq(SysAuthPageCapability::getTenantId, tenantId)
                        .eq(SysAuthPageCapability::getCatalogId, sourceCatalogId)
                        .in(SysAuthPageCapability::getId, sourceIds))
                .forEach(item -> sourceCodeById.put(item.getId(), item.getCapabilityCode()));
        Map<String, String> targetIdByCode = new LinkedHashMap<>();
        capabilityMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapability>()
                        .eq(SysAuthPageCapability::getTenantId, tenantId)
                        .eq(SysAuthPageCapability::getCatalogId, targetCatalogId)
                        .in(SysAuthPageCapability::getCapabilityCode, sourceCodeById.values()))
                .forEach(item -> targetIdByCode.put(item.getCapabilityCode(), item.getId()));
        sourceCodeById.forEach((id, code) -> mapping.put(id, targetIdByCode.get(code)));
        return mapping;
    }

    private SysAuthPageCatalog findActiveCatalog(String tenantId) {
        return catalogMapper.selectOne(new LambdaQueryWrapper<SysAuthPageCatalog>()
                .eq(SysAuthPageCatalog::getTenantId, tenantId)
                .eq(SysAuthPageCatalog::getLifecycleStatus, "ACTIVE")
                .orderByDesc(SysAuthPageCatalog::getCatalogVersion)
                .last("LIMIT 1"));
    }

    @Transactional
    public SysRoleAuthDraft replaceIntent(String draftId, ReplaceRoleCapabilityIntentRequest request) {
        if (request == null || request.getExpectedVersion() == null) {
            throw new BizException(40091, "ROLE_AUTH_DRAFT_VERSION_REQUIRED");
        }
        String tenantId = authorizationRegistryService.effectiveTenant(request.getTenantId());
        SysRoleAuthDraft draft = requireDraft(tenantId, draftId);
        if (!EDITABLE_STATUSES.contains(draft.getDraftStatus())) {
            throw new BizException(40992, "ROLE_AUTH_DRAFT_NOT_EDITABLE");
        }
        if (!request.getExpectedVersion().equals(draft.getIntentVersion())) {
            throw new BizException(40993, "ROLE_AUTH_DRAFT_VERSION_CONFLICT");
        }

        List<ReplaceRoleCapabilityIntentRequest.Selection> requestedSelections = request.getSelections() == null
                ? List.of() : request.getSelections();
        validateSelections(tenantId, draft.getCatalogId(), requestedSelections);
        List<ReplaceRoleCapabilityIntentRequest.Selection> selections = resolveDependencies(
                tenantId, draft.getCatalogId(), requestedSelections, request.getRemovedCapabilityIds());
        validatePolicyIntents(tenantId, draft.getCatalogId(), selections);
        String businessSummary = businessSummary(tenantId, draft.getCatalogId(), selections);

        intentMapper.delete(new LambdaQueryWrapper<SysRoleAuthIntent>()
                .eq(SysRoleAuthIntent::getTenantId, tenantId)
                .eq(SysRoleAuthIntent::getDraftId, draftId));
        for (ReplaceRoleCapabilityIntentRequest.Selection selection : selections) {
            SysRoleAuthIntent intent = new SysRoleAuthIntent();
            intent.setTenantId(tenantId);
            intent.setDraftId(draftId);
            intent.setCapabilityId(selection.getCapabilityId());
            intent.setSelectionSource(normalizeSource(selection.getSelectionSource()));
            intent.setDefaultScopeType(trimToNull(selection.getDefaultScopeType()));
            intent.setDefaultScopeIds(writeJson(selection.getDefaultScopeIds()));
            intent.setOperationScopeType(trimToNull(selection.getOperationScopeType()));
            intent.setOperationScopeIds(writeJson(selection.getOperationScopeIds()));
            intent.setFieldIntentJson(trimToNull(selection.getFieldIntentJson()));
            intent.setConstraintIntentJson(trimToNull(selection.getConstraintIntentJson()));
            intent.setCreatedBy(currentActor());
            intent.setUpdatedBy(currentActor());
            intentMapper.insert(intent);
        }

        int updated = draftMapper.update(null, new LambdaUpdateWrapper<SysRoleAuthDraft>()
                .eq(SysRoleAuthDraft::getId, draftId)
                .eq(SysRoleAuthDraft::getTenantId, tenantId)
                .eq(SysRoleAuthDraft::getIntentVersion, request.getExpectedVersion())
                .setSql("intent_version = intent_version + 1")
                .set(SysRoleAuthDraft::getDraftStatus, RoleAuthorizationDraftStatus.DRAFT.name())
                .set(SysRoleAuthDraft::getValidationTokenHash, null)
                .set(SysRoleAuthDraft::getValidationPlanHash, null)
                .set(SysRoleAuthDraft::getValidatedBy, null)
                .set(SysRoleAuthDraft::getValidationAuthorityVersion, null)
                .set(SysRoleAuthDraft::getValidatedAt, null)
                .set(SysRoleAuthDraft::getValidationExpiresAt, null)
                .set(SysRoleAuthDraft::getValidationSummary, businessSummary)
                .set(SysRoleAuthDraft::getUpdatedBy, currentActor()));
        if (updated != 1) {
            throw new BizException(40993, "ROLE_AUTH_DRAFT_VERSION_CONFLICT");
        }
        auditService.record(tenantId, draft.getRoleId(), draftId, null,
                "DRAFT_CHANGED", businessSummary, Map.of(
                        "expectedVersion", request.getExpectedVersion(),
                        "selectionCount", selections.size()));
        long dependencyCount = selections.stream()
                .filter(item -> "DEPENDENCY".equals(normalizeSource(item.getSelectionSource())))
                .count();
        if (dependencyCount > 0) {
            auditService.record(tenantId, draft.getRoleId(), draftId, null,
                    "DEPENDENCY_RESOLVED", "系统自动补齐了 " + dependencyCount + " 项必需功能",
                    Map.of("dependencyCount", dependencyCount));
        }
        return requireDraft(tenantId, draftId);
    }

    public SysRoleAuthDraft requireDraft(String requestedTenantId, String draftId) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        SysRoleAuthDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthDraft>()
                .eq(SysRoleAuthDraft::getTenantId, tenantId)
                .eq(SysRoleAuthDraft::getId, draftId));
        if (draft == null) {
            throw new BizException(40491, "ROLE_AUTH_DRAFT_NOT_FOUND");
        }
        return draft;
    }

    public RoleAuthorizationDraftResponse draftView(String requestedTenantId, String draftId) {
        SysRoleAuthDraft draft = requireDraft(requestedTenantId, draftId);
        List<SysRoleAuthIntent> intents = intentMapper.selectList(new LambdaQueryWrapper<SysRoleAuthIntent>()
                .eq(SysRoleAuthIntent::getTenantId, draft.getTenantId())
                .eq(SysRoleAuthIntent::getDraftId, draft.getId())
                .orderByAsc(SysRoleAuthIntent::getCreatedAt));
        Map<String, SysAuthPageCapability> capabilities = new LinkedHashMap<>();
        if (!intents.isEmpty()) {
            capabilityMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapability>()
                            .eq(SysAuthPageCapability::getTenantId, draft.getTenantId())
                            .eq(SysAuthPageCapability::getCatalogId, draft.getCatalogId())
                            .in(SysAuthPageCapability::getId,
                                    intents.stream().map(SysRoleAuthIntent::getCapabilityId).toList()))
                    .forEach(item -> capabilities.put(item.getId(), item));
        }
        List<RoleAuthorizationDraftResponse.Selection> selections = intents.stream()
                .map(intent -> selectionView(intent, capabilities.get(intent.getCapabilityId())))
                .toList();
        return RoleAuthorizationDraftResponse.builder()
                .draftId(draft.getId())
                .roleId(draft.getRoleId())
                .status(draft.getDraftStatus())
                .version(draft.getIntentVersion())
                .catalogId(draft.getCatalogId())
                .basedReleaseId(draft.getBasedReleaseId())
                .validatedAt(draft.getValidatedAt())
                .validationExpiresAt(draft.getValidationExpiresAt())
                .selections(selections)
                .build();
    }

    public RoleAuthorizationDraftResponse getOrCreateDraftView(String requestedTenantId, String roleId) {
        SysRoleAuthDraft draft = getOrCreateDraft(requestedTenantId, roleId);
        return draftView(draft.getTenantId(), draft.getId());
    }

    public RoleAuthorizationDraftResponse replaceIntentView(
            String draftId, ReplaceRoleCapabilityIntentRequest request) {
        SysRoleAuthDraft draft = replaceIntent(draftId, request);
        return draftView(draft.getTenantId(), draft.getId());
    }

    private RoleAuthorizationDraftResponse.Selection selectionView(
            SysRoleAuthIntent intent, SysAuthPageCapability capability) {
        String scopeType = StringUtils.hasText(intent.getOperationScopeType())
                ? intent.getOperationScopeType() : intent.getDefaultScopeType();
        return RoleAuthorizationDraftResponse.Selection.builder()
                .capabilityId(intent.getCapabilityId())
                .capabilityName(capability == null ? "功能定义已失效" : capability.getCapabilityName())
                .category(capability == null ? null : capability.getCapabilityCategory())
                .selectionSource(intent.getSelectionSource())
                .effectiveScopeSummary(StringUtils.hasText(scopeType) ? scopeLabel(scopeType) : "使用页面默认范围")
                .defaultScopeType(intent.getDefaultScopeType())
                .defaultScopeIds(readStringList(intent.getDefaultScopeIds()))
                .operationScopeType(intent.getOperationScopeType())
                .operationScopeIds(readStringList(intent.getOperationScopeIds()))
                .fieldIntentJson(intent.getFieldIntentJson())
                .constraintIntentJson(intent.getConstraintIntentJson())
                .build();
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException exception) {
            throw new BizException(40995, "数据范围定义无法读取");
        }
    }

    private void validateSelections(String tenantId, String catalogId,
                                    List<ReplaceRoleCapabilityIntentRequest.Selection> selections) {
        Set<String> capabilityIds = new HashSet<>();
        for (ReplaceRoleCapabilityIntentRequest.Selection selection : selections) {
            if (selection == null || !StringUtils.hasText(selection.getCapabilityId())) {
                throw new BizException(40092, "PAGE_CAPABILITY_REQUIRED");
            }
            if (!capabilityIds.add(selection.getCapabilityId().trim())) {
                throw new BizException(40093, "PAGE_CAPABILITY_DUPLICATE");
            }
        }
        if (capabilityIds.isEmpty()) {
            return;
        }
        Long matching = capabilityMapper.selectCount(new LambdaQueryWrapper<SysAuthPageCapability>()
                .eq(SysAuthPageCapability::getTenantId, tenantId)
                .eq(SysAuthPageCapability::getCatalogId, catalogId)
                .eq(SysAuthPageCapability::getStatus, (short) 1)
                .in(SysAuthPageCapability::getId, capabilityIds));
        if (matching == null || matching != capabilityIds.size()) {
            throw new BizException(40391, "PAGE_CAPABILITY_TENANT_OR_CATALOG_MISMATCH");
        }
    }

    private List<ReplaceRoleCapabilityIntentRequest.Selection> resolveDependencies(
            String tenantId, String catalogId,
            List<ReplaceRoleCapabilityIntentRequest.Selection> requested,
            List<String> removedCapabilityIds) {
        List<SysAuthPageCapability> catalogCapabilities = capabilityMapper.selectList(
                new LambdaQueryWrapper<SysAuthPageCapability>()
                        .eq(SysAuthPageCapability::getTenantId, tenantId)
                        .eq(SysAuthPageCapability::getCatalogId, catalogId)
                        .eq(SysAuthPageCapability::getStatus, (short) 1));
        Map<String, SysAuthPageCapability> capabilitiesById = new LinkedHashMap<>();
        catalogCapabilities.forEach(item -> capabilitiesById.put(item.getId(), item));
        Map<String, List<String>> requiredByCapability = new LinkedHashMap<>();
        if (!catalogCapabilities.isEmpty()) {
            dependencyMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapabilityDependency>()
                            .eq(SysAuthPageCapabilityDependency::getTenantId, tenantId)
                            .in(SysAuthPageCapabilityDependency::getCapabilityId, capabilitiesById.keySet()))
                    .forEach(item -> requiredByCapability
                            .computeIfAbsent(item.getCapabilityId(), ignored -> new ArrayList<>())
                            .add(item.getRequiredCapabilityId()));
        }
        Set<String> explicitlyRemoved = removedCapabilityIds == null
                ? Set.of() : new HashSet<>(removedCapabilityIds);
        Map<String, ReplaceRoleCapabilityIntentRequest.Selection> resolved = new LinkedHashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (ReplaceRoleCapabilityIntentRequest.Selection selection : requested) {
            String capabilityId = selection.getCapabilityId().trim();
            resolved.put(capabilityId, selection);
            queue.add(capabilityId);
        }
        while (!queue.isEmpty()) {
            String sourceId = queue.removeFirst();
            for (String requiredId : requiredByCapability.getOrDefault(sourceId, List.of())) {
                if (explicitlyRemoved.contains(requiredId)) {
                    SysAuthPageCapability source = capabilitiesById.get(sourceId);
                    SysAuthPageCapability required = capabilitiesById.get(requiredId);
                    throw new BizException(40994, "不能取消“" + required.getCapabilityName()
                            + "”，因为“" + source.getCapabilityName() + "”仍然需要它");
                }
                if (!resolved.containsKey(requiredId)) {
                    ReplaceRoleCapabilityIntentRequest.Selection dependency =
                            new ReplaceRoleCapabilityIntentRequest.Selection();
                    dependency.setCapabilityId(requiredId);
                    dependency.setSelectionSource("DEPENDENCY");
                    resolved.put(requiredId, dependency);
                    queue.add(requiredId);
                }
            }
        }
        return new ArrayList<>(resolved.values());
    }

    private String businessSummary(String tenantId, String catalogId,
                                   List<ReplaceRoleCapabilityIntentRequest.Selection> selections) {
        if (selections.isEmpty()) {
            return "该角色暂未获得任何页面功能";
        }
        Map<String, SysAuthPageCapability> byId = new LinkedHashMap<>();
        capabilityMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapability>()
                        .eq(SysAuthPageCapability::getTenantId, tenantId)
                        .eq(SysAuthPageCapability::getCatalogId, catalogId)
                        .in(SysAuthPageCapability::getId,
                                selections.stream().map(ReplaceRoleCapabilityIntentRequest.Selection::getCapabilityId).toList()))
                .forEach(item -> byId.put(item.getId(), item));
        Map<String, List<String>> operationsByPage = new LinkedHashMap<>();
        for (ReplaceRoleCapabilityIntentRequest.Selection selection : selections) {
            SysAuthPageCapability capability = byId.get(selection.getCapabilityId());
            if (capability != null) {
                String scopeType = StringUtils.hasText(selection.getOperationScopeType())
                        ? selection.getOperationScopeType() : selection.getDefaultScopeType();
                String description = capability.getCapabilityName()
                        + (StringUtils.hasText(scopeType) ? "（" + scopeLabel(scopeType) + "）" : "");
                operationsByPage.computeIfAbsent(capability.getPageName(), ignored -> new ArrayList<>())
                        .add(description);
            }
        }
        return operationsByPage.entrySet().stream()
                .map(entry -> entry.getKey() + "：" + String.join("、", entry.getValue()))
                .collect(java.util.stream.Collectors.joining("；"));
    }

    private void validatePolicyIntents(String tenantId, String catalogId,
                                       List<ReplaceRoleCapabilityIntentRequest.Selection> selections) {
        if (selections.isEmpty()) {
            return;
        }
        Map<String, SysAuthPageCapability> capabilities = new LinkedHashMap<>();
        capabilityMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapability>()
                        .eq(SysAuthPageCapability::getTenantId, tenantId)
                        .eq(SysAuthPageCapability::getCatalogId, catalogId)
                        .in(SysAuthPageCapability::getId,
                                selections.stream().map(ReplaceRoleCapabilityIntentRequest.Selection::getCapabilityId).toList()))
                .forEach(item -> capabilities.put(item.getId(), item));
        for (ReplaceRoleCapabilityIntentRequest.Selection selection : selections) {
            SysAuthPageCapability capability = capabilities.get(selection.getCapabilityId());
            if (capability == null) {
                throw new BizException(40391, "PAGE_CAPABILITY_TENANT_OR_CATALOG_MISMATCH");
            }
            validateScope(capability, selection.getDefaultScopeType(), selection.getDefaultScopeIds(), false);
            validateScope(capability, selection.getOperationScopeType(), selection.getOperationScopeIds(), true);
            if (StringUtils.hasText(selection.getFieldIntentJson())
                    && !enabled(capability.getFieldPolicySupported())) {
                throw new BizException(40995, "该页面功能尚不支持有效的字段限制");
            }
            if (StringUtils.hasText(selection.getConstraintIntentJson())
                    && !hasDeclaredGuards(capability)) {
                throw new BizException(40995, "该页面功能没有可执行的业务限制");
            }
        }
    }

    private void validateScope(SysAuthPageCapability capability, String scopeType,
                               List<String> scopeIds, boolean operationOverride) {
        if (!StringUtils.hasText(scopeType)) {
            return;
        }
        if (!enabled(capability.getScopeSupported())) {
            throw new BizException(40995, "“" + capability.getCapabilityName() + "”不支持数据范围配置");
        }
        if (operationOverride && !"OPERATION".equals(capability.getCapabilityCategory())) {
            throw new BizException(40995, "只有业务操作可以单独设置操作范围");
        }
        if (!operationOverride && !"READ".equals(capability.getCapabilityCategory())) {
            throw new BizException(40995, "页面默认查看范围必须配置在查看功能上");
        }
        String normalized = scopeType.trim().toUpperCase();
        if (!SCOPE_TYPES.contains(normalized)) {
            throw new BizException(40095, "ROLE_AUTH_SCOPE_TYPE_INVALID");
        }
        if ("ASSIGNED_ORGS".equals(normalized)
                && (scopeIds == null || scopeIds.stream().noneMatch(StringUtils::hasText))) {
            throw new BizException(40095, "指定组织范围至少需要选择一个组织");
        }
    }

    private boolean hasDeclaredGuards(SysAuthPageCapability capability) {
        if (!StringUtils.hasText(capability.getMetadataJson())) {
            return false;
        }
        try {
            com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest.Capability manifest =
                    objectMapper.readValue(capability.getMetadataJson(),
                            com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest.Capability.class);
            return manifest.getGuardCodes() != null && !manifest.getGuardCodes().isEmpty();
        } catch (JsonProcessingException exception) {
            throw new BizException(40995, "页面功能定义无法读取");
        }
    }

    private boolean enabled(Short value) {
        return value != null && value == 1;
    }

    private String scopeLabel(String scopeType) {
        return switch (scopeType.trim().toUpperCase()) {
            case "NONE" -> "不可查看数据";
            case "SELF" -> "仅本人数据";
            case "OWN_ORG" -> "本部门数据";
            case "OWN_ORG_AND_CHILDREN" -> "本部门及下级部门数据";
            case "ASSIGNED_ORGS" -> "指定组织数据";
            case "ALL" -> "全部数据";
            default -> "范围待确认";
        };
    }

    private void requireRole(String tenantId, String roleId) {
        if (!StringUtils.hasText(roleId) || roleMapper.selectCount(new LambdaQueryWrapper<com.triobase.service.auth.entity.SysRole>()
                .eq(com.triobase.service.auth.entity.SysRole::getTenantId, tenantId)
                .eq(com.triobase.service.auth.entity.SysRole::getId, roleId.trim())) == 0) {
            throw new BizException(40492, "ROLE_NOT_FOUND");
        }
    }

    private String normalizeSource(String value) {
        String source = StringUtils.hasText(value) ? value.trim().toUpperCase() : "EXPLICIT";
        if (!Set.of("EXPLICIT", "DEPENDENCY", "MIGRATION").contains(source)) {
            throw new BizException(40094, "ROLE_AUTH_SELECTION_SOURCE_INVALID");
        }
        return source;
    }

    private String writeJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new BizException(40095, "ROLE_AUTH_SCOPE_IDS_INVALID");
        }
    }

    private String currentActor() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
