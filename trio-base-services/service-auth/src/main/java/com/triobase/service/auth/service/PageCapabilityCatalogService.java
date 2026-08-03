package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import com.triobase.service.auth.dto.PageCapabilityDiagnosticResponse;
import com.triobase.service.auth.dto.PageCapabilityResponse;
import com.triobase.service.auth.entity.PageCapabilityCategory;
import com.triobase.service.auth.entity.PageCapabilityReadiness;
import com.triobase.service.auth.entity.SysAuthAction;
import com.triobase.service.auth.entity.SysAuthGuardTemplate;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCapabilityDependency;
import com.triobase.service.auth.entity.SysAuthPageCapabilityTarget;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.entity.SysAuthResource;
import com.triobase.service.auth.entity.SysMenu;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthGuardTemplateMapper;
import com.triobase.service.auth.mapper.AuthFieldMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityDependencyMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityTargetMapper;
import com.triobase.service.auth.mapper.AuthPageCatalogMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import com.triobase.service.auth.mapper.MenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PageCapabilityCatalogService {

    private static final Set<String> SOURCE_TYPES = Set.of(
            "SYSTEM_MANIFEST", "OWNER_MANIFEST", "LOWCODE_PUBLICATION");
    private static final Set<String> TARGET_KINDS = Set.of(
            "GRANT", "DATA_POLICY", "FIELD_POLICY", "GUARD");

    private final AuthPageCatalogMapper catalogMapper;
    private final AuthPageCapabilityMapper capabilityMapper;
    private final AuthPageCapabilityTargetMapper targetMapper;
    private final AuthPageCapabilityDependencyMapper dependencyMapper;
    private final AuthResourceMapper resourceMapper;
    private final AuthActionMapper actionMapper;
    private final AuthGuardTemplateMapper guardTemplateMapper;
    private final AuthFieldMapper fieldMapper;
    private final MenuMapper menuMapper;
    private final AuthorizationRegistryService authorizationRegistryService;
    private final RoleAuthorizationDriftService driftService;
    private final ObjectMapper objectMapper;

    public List<PageCapabilityResponse> implementationCatalog(
            String requestedTenantId, String requestedCatalogId, String requestedPageCode) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        SysAuthPageCatalog catalog = resolveCatalog(tenantId, requestedCatalogId);
        LambdaQueryWrapper<SysAuthPageCapability> query = new LambdaQueryWrapper<SysAuthPageCapability>()
                        .eq(SysAuthPageCapability::getTenantId, tenantId)
                        .eq(SysAuthPageCapability::getCatalogId, catalog.getId())
                        .eq(SysAuthPageCapability::getStatus, (short) 1);
        if (StringUtils.hasText(requestedPageCode)) {
            query.eq(SysAuthPageCapability::getPageCode, requestedPageCode.trim());
        }
        List<SysAuthPageCapability> capabilities = capabilityMapper.selectList(query
                        .orderByAsc(SysAuthPageCapability::getPageCode)
                        .orderByAsc(SysAuthPageCapability::getSortOrder));
        Map<String, List<String>> dependenciesByCapability = new HashMap<>();
        Map<String, List<SysAuthPageCapabilityTarget>> targetsByCapability = new HashMap<>();
        if (!capabilities.isEmpty()) {
            dependencyMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapabilityDependency>()
                            .eq(SysAuthPageCapabilityDependency::getTenantId, tenantId)
                            .in(SysAuthPageCapabilityDependency::getCapabilityId,
                                    capabilities.stream().map(SysAuthPageCapability::getId).toList()))
                    .forEach(item -> dependenciesByCapability
                            .computeIfAbsent(item.getCapabilityId(), ignored -> new ArrayList<>())
                            .add(item.getRequiredCapabilityId()));
            targetMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapabilityTarget>()
                            .eq(SysAuthPageCapabilityTarget::getTenantId, tenantId)
                            .eq(SysAuthPageCapabilityTarget::getStatus, (short) 1)
                            .in(SysAuthPageCapabilityTarget::getCapabilityId,
                                    capabilities.stream().map(SysAuthPageCapability::getId).toList()))
                    .forEach(item -> targetsByCapability
                            .computeIfAbsent(item.getCapabilityId(), ignored -> new ArrayList<>()).add(item));
        }
        return capabilities.stream().map(item -> {
            PageCapabilityResponse response = PageCapabilityResponse.from(item);
            response.setScopeConfigurable(
                    enabled(item.getScopeSupported())
                            && hasVerifiedDataScopeTarget(
                                    tenantId,
                                    targetsByCapability.getOrDefault(item.getId(), List.of())));
            response.setRequiredCapabilityIds(
                    dependenciesByCapability.getOrDefault(item.getId(), List.of()));
            if (enabled(item.getFieldPolicySupported())) {
                Set<String> resourceCodes = targetsByCapability.getOrDefault(item.getId(), List.of())
                        .stream().map(SysAuthPageCapabilityTarget::getResourceCode).collect(java.util.stream.Collectors.toSet());
                if (!resourceCodes.isEmpty()) {
                    response.setAvailableFields(fieldMapper.selectList(
                                    new LambdaQueryWrapper<com.triobase.service.auth.entity.SysAuthField>()
                                            .eq(com.triobase.service.auth.entity.SysAuthField::getTenantId, tenantId)
                                            .eq(com.triobase.service.auth.entity.SysAuthField::getStatus, (short) 1)
                                            .in(com.triobase.service.auth.entity.SysAuthField::getResourceCode, resourceCodes))
                            .stream().map(field -> {
                                PageCapabilityResponse.FieldOption option = new PageCapabilityResponse.FieldOption();
                                option.setFieldKey(field.getFieldKey());
                                option.setFieldLabel(field.getFieldLabel());
                                return option;
                            }).toList());
                }
            }
            response.setFieldRestrictionConfigurable(
                    enabled(item.getFieldPolicySupported())
                            && hasReadyFieldTarget(
                                    tenantId,
                                    targetsByCapability.getOrDefault(item.getId(), List.of())));
            response.setConstraintConfigurable(hasDeclaredGuards(item));
            return response;
        }).toList();
    }

    private boolean hasVerifiedDataScopeTarget(
            String tenantId,
            List<SysAuthPageCapabilityTarget> targets) {
        return targets.stream().anyMatch(target -> actionMapper.selectCount(
                new LambdaQueryWrapper<SysAuthAction>()
                        .eq(SysAuthAction::getTenantId, tenantId)
                        .eq(SysAuthAction::getResourceCode, target.getResourceCode())
                        .eq(SysAuthAction::getActionCode, target.getActionCode())
                        .eq(SysAuthAction::getDataScopeSupported, (short) 1)
                        .eq(SysAuthAction::getDataScopeEnforced, (short) 1)
                        .eq(SysAuthAction::getStatus, (short) 1)) > 0);
    }

    private boolean hasReadyFieldTarget(
            String tenantId,
            List<SysAuthPageCapabilityTarget> targets) {
        return targets.stream().anyMatch(target -> {
            SysAuthResource resource = resourceMapper.selectOne(
                    new LambdaQueryWrapper<SysAuthResource>()
                            .eq(SysAuthResource::getTenantId, tenantId)
                            .eq(SysAuthResource::getResourceCode, target.getResourceCode())
                            .eq(SysAuthResource::getLifecycleStatus, "ACTIVE")
                            .last("LIMIT 1"));
            if (resource == null
                    || !enabled(resource.getReadHideEnforced())
                    || !enabled(resource.getReadMaskEnforced())
                    || !enabled(resource.getWriteDenyEnforced())) {
                return false;
            }
            return fieldMapper.selectCount(new LambdaQueryWrapper<com.triobase.service.auth.entity.SysAuthField>()
                    .eq(com.triobase.service.auth.entity.SysAuthField::getTenantId, tenantId)
                    .eq(com.triobase.service.auth.entity.SysAuthField::getResourceCode, target.getResourceCode())
                    .eq(com.triobase.service.auth.entity.SysAuthField::getStatus, (short) 1)) > 0;
        });
    }

    public boolean pageExists(String requestedTenantId, String pageCode) {
        return StringUtils.hasText(pageCode)
                && !implementationCatalog(requestedTenantId, null, pageCode.trim()).isEmpty();
    }

    public List<SysAuthPageCatalog> catalogs(String requestedTenantId) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        return catalogMapper.selectList(new LambdaQueryWrapper<SysAuthPageCatalog>()
                .eq(SysAuthPageCatalog::getTenantId, tenantId)
                .orderByDesc(SysAuthPageCatalog::getCatalogVersion)
                .orderByDesc(SysAuthPageCatalog::getUpdatedAt));
    }

    private boolean hasDeclaredGuards(SysAuthPageCapability capability) {
        if (!StringUtils.hasText(capability.getMetadataJson())) return false;
        try {
            PageCapabilityManifestSyncRequest.Capability manifest = objectMapper.readValue(
                    capability.getMetadataJson(), PageCapabilityManifestSyncRequest.Capability.class);
            return manifest.getGuardCodes() != null && !manifest.getGuardCodes().isEmpty();
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    public List<PageCapabilityDiagnosticResponse> diagnostics(String requestedTenantId,
                                                               String requestedCatalogId) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        SysAuthPageCatalog catalog = resolveCatalog(tenantId, requestedCatalogId);
        List<SysAuthPageCapability> capabilities = capabilityMapper.selectList(
                new LambdaQueryWrapper<SysAuthPageCapability>()
                        .eq(SysAuthPageCapability::getTenantId, tenantId)
                        .eq(SysAuthPageCapability::getCatalogId, catalog.getId())
                        .orderByAsc(SysAuthPageCapability::getPageCode)
                        .orderByAsc(SysAuthPageCapability::getSortOrder));
        if (capabilities.isEmpty()) {
            return List.of();
        }
        Map<String, String> codesById = new HashMap<>();
        capabilities.forEach(item -> codesById.put(item.getId(), item.getCapabilityCode()));
        Map<String, List<String>> dependenciesByCapability = new HashMap<>();
        dependencyMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapabilityDependency>()
                        .eq(SysAuthPageCapabilityDependency::getTenantId, tenantId)
                        .in(SysAuthPageCapabilityDependency::getCapabilityId,
                                capabilities.stream().map(SysAuthPageCapability::getId).toList()))
                .forEach(item -> dependenciesByCapability
                        .computeIfAbsent(item.getCapabilityId(), ignored -> new ArrayList<>())
                        .add(codesById.get(item.getRequiredCapabilityId())));
        Map<String, List<SysAuthPageCapabilityTarget>> targetsByCapability = new HashMap<>();
        targetMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapabilityTarget>()
                        .eq(SysAuthPageCapabilityTarget::getTenantId, tenantId)
                        .in(SysAuthPageCapabilityTarget::getCapabilityId,
                                capabilities.stream().map(SysAuthPageCapability::getId).toList()))
                .forEach(item -> targetsByCapability
                        .computeIfAbsent(item.getCapabilityId(), ignored -> new ArrayList<>()).add(item));
        return capabilities.stream().map(item -> PageCapabilityDiagnosticResponse.builder()
                .capabilityId(item.getId())
                .tenantId(tenantId)
                .catalogId(catalog.getId())
                .catalogVersion(catalog.getCatalogVersion())
                .pageCode(item.getPageCode())
                .capabilityCode(item.getCapabilityCode())
                .readiness(item.getReadinessStatus())
                .readinessMessage(item.getReadinessMessage())
                .requiredCapabilityCodes(dependenciesByCapability.getOrDefault(item.getId(), List.of()))
                .targets(targetsByCapability.getOrDefault(item.getId(), List.of()).stream()
                        .map(target -> PageCapabilityDiagnosticResponse.Target.builder()
                                .resourceCode(target.getResourceCode())
                                .actionCode(target.getActionCode())
                                .targetKind(target.getTargetKind())
                                .required(enabled(target.getRequiredFlag()))
                                .active(enabled(target.getStatus()))
                                .build())
                        .toList())
                .build()).toList();
    }

    @Transactional
    public SysAuthPageCatalog synchronize(PageCapabilityManifestSyncRequest request) {
        validateManifestHeader(request);
        String tenantId = authorizationRegistryService.effectiveTenant(request.getTenantId());
        String catalogCode = required(request.getCatalogCode(), "PAGE_CAPABILITY_CATALOG_CODE_REQUIRED");
        String sourceType = required(request.getSourceType(), "PAGE_CAPABILITY_SOURCE_TYPE_REQUIRED").toUpperCase();
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw new BizException(40097, "PAGE_CAPABILITY_SOURCE_TYPE_INVALID");
        }
        validateCapabilityGraph(request.getPages());
        String manifestHash = manifestHash(request);

        SysAuthPageCatalog existing = catalogMapper.selectOne(new LambdaQueryWrapper<SysAuthPageCatalog>()
                .eq(SysAuthPageCatalog::getTenantId, tenantId)
                .eq(SysAuthPageCatalog::getCatalogCode, catalogCode)
                .eq(SysAuthPageCatalog::getCatalogVersion, request.getCatalogVersion()));
        if (existing != null) {
            if (!manifestHash.equals(existing.getManifestHash())) {
                throw new BizException(40997, "PAGE_CAPABILITY_VERSION_CONTENT_CONFLICT");
            }
            return existing;
        }

        SysAuthPageCatalog catalog = new SysAuthPageCatalog();
        catalog.setTenantId(tenantId);
        catalog.setCatalogCode(catalogCode);
        catalog.setCatalogVersion(request.getCatalogVersion());
        catalog.setSourceType(sourceType);
        catalog.setSourceRef(trimToNull(request.getSourceRef()));
        catalog.setManifestHash(manifestHash);
        catalog.setLifecycleStatus("DRAFT");
        catalog.setCreatedBy(currentActor());
        catalog.setUpdatedBy(currentActor());
        catalogMapper.insert(catalog);

        Map<String, SysAuthPageCapability> persisted = new LinkedHashMap<>();
        for (PageCapabilityManifestSyncRequest.Page page : safe(request.getPages())) {
            SysMenu menu = findMenu(page.getMenuKey());
            for (PageCapabilityManifestSyncRequest.Capability item : safe(page.getCapabilities())) {
                Readiness readiness = evaluateReadiness(tenantId, menu, item);
                SysAuthPageCapability capability = new SysAuthPageCapability();
                capability.setTenantId(tenantId);
                capability.setCatalogId(catalog.getId());
                capability.setMenuId(menu != null ? menu.getId() : null);
                capability.setPageCode(required(page.getPageCode(), "PAGE_CAPABILITY_PAGE_CODE_REQUIRED"));
                capability.setPageName(required(page.getPageName(), "PAGE_CAPABILITY_PAGE_NAME_REQUIRED"));
                capability.setCapabilityCode(required(item.getCapabilityCode(), "PAGE_CAPABILITY_CODE_REQUIRED"));
                capability.setCapabilityName(required(item.getCapabilityName(), "PAGE_CAPABILITY_NAME_REQUIRED"));
                capability.setCapabilityCategory(normalizeCategory(item.getCategory()));
                capability.setHelpText(trimToNull(item.getHelpText()));
                capability.setReadinessStatus(readiness.status().name());
                capability.setReadinessMessage(readiness.message());
                capability.setScopeSupported(flag(item.getScopeSupported()));
                capability.setFieldPolicySupported(flag(item.getFieldEnforcement() != null
                        && item.getFieldEnforcement().anyRequired()));
                capability.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                capability.setStatus((short) 1);
                capability.setMetadataJson(writeJson(item));
                capability.setCreatedBy(currentActor());
                capability.setUpdatedBy(currentActor());
                capabilityMapper.insert(capability);
                persisted.put(capability.getCapabilityCode(), capability);

                for (PageCapabilityManifestSyncRequest.Target itemTarget : safe(item.getTargets())) {
                    SysAuthPageCapabilityTarget target = new SysAuthPageCapabilityTarget();
                    target.setTenantId(tenantId);
                    target.setCapabilityId(capability.getId());
                    target.setResourceCode(required(itemTarget.getResourceCode(), "PAGE_CAPABILITY_RESOURCE_REQUIRED"));
                    target.setActionCode(required(itemTarget.getActionCode(), "PAGE_CAPABILITY_ACTION_REQUIRED"));
                    target.setTargetKind(normalizeTargetKind(itemTarget.getTargetKind()));
                    target.setRequiredFlag(flag(!Boolean.FALSE.equals(itemTarget.getRequired())));
                    target.setStatus((short) 1);
                    target.setCreatedBy(currentActor());
                    target.setUpdatedBy(currentActor());
                    targetMapper.insert(target);
                }
            }
        }

        for (PageCapabilityManifestSyncRequest.Page page : safe(request.getPages())) {
            for (PageCapabilityManifestSyncRequest.Capability item : safe(page.getCapabilities())) {
                SysAuthPageCapability source = persisted.get(item.getCapabilityCode());
                for (String requiredCode : safe(item.getRequiredCapabilityCodes())) {
                    SysAuthPageCapability requiredCapability = persisted.get(requiredCode);
                    if (requiredCapability == null) {
                        throw new BizException(40098, "PAGE_CAPABILITY_DEPENDENCY_NOT_FOUND");
                    }
                    SysAuthPageCapabilityDependency dependency = new SysAuthPageCapabilityDependency();
                    dependency.setTenantId(tenantId);
                    dependency.setCapabilityId(source.getId());
                    dependency.setRequiredCapabilityId(requiredCapability.getId());
                    dependency.setCreatedBy(currentActor());
                    dependencyMapper.insert(dependency);
                }
            }
        }
        return catalog;
    }

    @Transactional
    public SysAuthPageCatalog activate(String requestedTenantId, String catalogId) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        SysAuthPageCatalog catalog = catalogMapper.selectOne(new LambdaQueryWrapper<SysAuthPageCatalog>()
                .eq(SysAuthPageCatalog::getTenantId, tenantId)
                .eq(SysAuthPageCatalog::getId, catalogId));
        if (catalog == null) {
            throw new BizException(40497, "PAGE_CAPABILITY_CATALOG_NOT_FOUND");
        }
        if ("ACTIVE".equals(catalog.getLifecycleStatus())) {
            return catalog;
        }
        Long invalid = capabilityMapper.selectCount(new LambdaQueryWrapper<SysAuthPageCapability>()
                .eq(SysAuthPageCapability::getTenantId, tenantId)
                .eq(SysAuthPageCapability::getCatalogId, catalogId)
                .eq(SysAuthPageCapability::getStatus, (short) 1)
                .ne(SysAuthPageCapability::getReadinessStatus, PageCapabilityReadiness.READY.name()));
        if (invalid != null && invalid > 0) {
            throw new BizException(40998, "PAGE_CAPABILITY_CATALOG_NOT_READY");
        }
        catalogMapper.update(null, new LambdaUpdateWrapper<SysAuthPageCatalog>()
                .eq(SysAuthPageCatalog::getTenantId, tenantId)
                .eq(SysAuthPageCatalog::getCatalogCode, catalog.getCatalogCode())
                .eq(SysAuthPageCatalog::getLifecycleStatus, "ACTIVE")
                .set(SysAuthPageCatalog::getLifecycleStatus, "SUPERSEDED")
                .set(SysAuthPageCatalog::getUpdatedBy, currentActor()));
        int activated = catalogMapper.update(null, new LambdaUpdateWrapper<SysAuthPageCatalog>()
                .eq(SysAuthPageCatalog::getTenantId, tenantId)
                .eq(SysAuthPageCatalog::getId, catalogId)
                .in(SysAuthPageCatalog::getLifecycleStatus, "DRAFT", "ACTIVE")
                .set(SysAuthPageCatalog::getLifecycleStatus, "ACTIVE")
                .setSql("activated_at = CURRENT_TIMESTAMP")
                .set(SysAuthPageCatalog::getUpdatedBy, currentActor()));
        if (activated != 1) {
            throw new BizException(40999, "PAGE_CAPABILITY_CATALOG_ACTIVATION_CONFLICT");
        }
        SysAuthPageCatalog activatedCatalog = catalogMapper.selectById(catalogId);
        driftService.detectForActivatedCatalog(activatedCatalog);
        return activatedCatalog;
    }

    private Readiness evaluateReadiness(String tenantId, SysMenu menu,
                                        PageCapabilityManifestSyncRequest.Capability capability) {
        List<String> blocking = new ArrayList<>();
        boolean partial = false;
        if (menu == null) {
            blocking.add("所属页面尚未登记");
        }
        List<PageCapabilityManifestSyncRequest.Target> targets = safe(capability.getTargets());
        if (targets.isEmpty()) {
            return new Readiness(PageCapabilityReadiness.UNMAPPED, "该功能尚未连接运行时权限");
        }
        for (PageCapabilityManifestSyncRequest.Target target : targets) {
            boolean required = !Boolean.FALSE.equals(target.getRequired());
            SysAuthResource resource = resourceMapper.selectOne(new LambdaQueryWrapper<SysAuthResource>()
                    .eq(SysAuthResource::getTenantId, tenantId)
                    .eq(SysAuthResource::getResourceCode, target.getResourceCode())
                    .eq(SysAuthResource::getLifecycleStatus, "ACTIVE"));
            Long actionCount = actionMapper.selectCount(new LambdaQueryWrapper<SysAuthAction>()
                    .eq(SysAuthAction::getTenantId, tenantId)
                    .eq(SysAuthAction::getResourceCode, target.getResourceCode())
                    .eq(SysAuthAction::getActionCode, target.getActionCode())
                    .eq(SysAuthAction::getStatus, (short) 1));
            boolean valid = resource != null && actionCount != null && actionCount > 0;
            if (!valid) {
                if (required) {
                    blocking.add("存在未就绪的必需后台功能");
                } else {
                    partial = true;
                }
                continue;
            }
            PageCapabilityManifestSyncRequest.FieldEnforcementRequirement fields = capability.getFieldEnforcement();
            if (fields != null && ((Boolean.TRUE.equals(fields.getReadHideRequired()) && !enabled(resource.getReadHideEnforced()))
                    || (Boolean.TRUE.equals(fields.getReadMaskRequired()) && !enabled(resource.getReadMaskEnforced()))
                    || (Boolean.TRUE.equals(fields.getWriteDenyRequired()) && !enabled(resource.getWriteDenyEnforced())))) {
                blocking.add("字段保护尚未由业务服务完整执行");
            }
        }
        for (String guardCode : safe(capability.getGuardCodes())) {
            Long guardCount = guardTemplateMapper.selectCount(new LambdaQueryWrapper<SysAuthGuardTemplate>()
                    .eq(SysAuthGuardTemplate::getTenantId, tenantId)
                    .eq(SysAuthGuardTemplate::getGuardCode, guardCode)
                    .eq(SysAuthGuardTemplate::getStatus, (short) 1));
            if (guardCount == null || guardCount == 0) {
                blocking.add("业务限制尚未就绪");
            }
        }
        if (!blocking.isEmpty()) {
            return new Readiness(PageCapabilityReadiness.BROKEN, String.join("；", new HashSet<>(blocking)));
        }
        return partial
                ? new Readiness(PageCapabilityReadiness.PARTIAL, "可选功能尚未全部连接")
                : new Readiness(PageCapabilityReadiness.READY, "可以用于角色授权");
    }

    private void validateManifestHeader(PageCapabilityManifestSyncRequest request) {
        if (request == null || request.getCatalogVersion() == null || request.getCatalogVersion() <= 0) {
            throw new BizException(40096, "PAGE_CAPABILITY_MANIFEST_REQUIRED");
        }
    }

    private void validateCapabilityGraph(List<PageCapabilityManifestSyncRequest.Page> pages) {
        Map<String, List<String>> graph = new HashMap<>();
        for (PageCapabilityManifestSyncRequest.Page page : safe(pages)) {
            for (PageCapabilityManifestSyncRequest.Capability capability : safe(page.getCapabilities())) {
                String code = required(capability.getCapabilityCode(), "PAGE_CAPABILITY_CODE_REQUIRED");
                if (graph.putIfAbsent(code, new ArrayList<>(safe(capability.getRequiredCapabilityCodes()))) != null) {
                    throw new BizException(40099, "PAGE_CAPABILITY_CODE_DUPLICATE");
                }
                normalizeCategory(capability.getCategory());
            }
        }
        for (Map.Entry<String, List<String>> entry : graph.entrySet()) {
            for (String dependency : entry.getValue()) {
                if (!graph.containsKey(dependency)) {
                    throw new BizException(40098, "PAGE_CAPABILITY_DEPENDENCY_NOT_FOUND");
                }
            }
        }
        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();
        for (String code : graph.keySet()) {
            detectCycle(code, graph, visited, active);
        }
    }

    private void detectCycle(String code, Map<String, List<String>> graph,
                             Set<String> visited, Set<String> active) {
        if (active.contains(code)) {
            throw new BizException(40996, "PAGE_CAPABILITY_DEPENDENCY_CYCLE");
        }
        if (!visited.add(code)) {
            return;
        }
        active.add(code);
        for (String dependency : graph.getOrDefault(code, List.of())) {
            detectCycle(dependency, graph, visited, active);
        }
        active.remove(code);
    }

    private SysMenu findMenu(String menuKey) {
        if (!StringUtils.hasText(menuKey)) {
            return null;
        }
        return menuMapper.selectOne(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getMenuKey, menuKey.trim())
                .eq(SysMenu::getStatus, (short) 1));
    }

    private SysAuthPageCatalog resolveCatalog(String tenantId, String requestedCatalogId) {
        LambdaQueryWrapper<SysAuthPageCatalog> query = new LambdaQueryWrapper<SysAuthPageCatalog>()
                .eq(SysAuthPageCatalog::getTenantId, tenantId);
        if (StringUtils.hasText(requestedCatalogId)) {
            query.eq(SysAuthPageCatalog::getId, requestedCatalogId.trim());
        } else {
            query.eq(SysAuthPageCatalog::getLifecycleStatus, "ACTIVE")
                    .orderByDesc(SysAuthPageCatalog::getCatalogVersion)
                    .last("LIMIT 1");
        }
        SysAuthPageCatalog catalog = catalogMapper.selectOne(query);
        if (catalog == null) {
            throw new BizException(40497, "PAGE_CAPABILITY_CATALOG_NOT_FOUND");
        }
        return catalog;
    }

    private String manifestHash(PageCapabilityManifestSyncRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new BizException(40096, "PAGE_CAPABILITY_MANIFEST_INVALID");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException(40096, "PAGE_CAPABILITY_MANIFEST_INVALID");
        }
    }

    private String normalizeCategory(String category) {
        try {
            return PageCapabilityCategory.valueOf(required(category,
                    "PAGE_CAPABILITY_CATEGORY_REQUIRED").toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new BizException(40097, "PAGE_CAPABILITY_CATEGORY_INVALID");
        }
    }

    private String normalizeTargetKind(String targetKind) {
        String normalized = StringUtils.hasText(targetKind) ? targetKind.trim().toUpperCase() : "GRANT";
        if (!TARGET_KINDS.contains(normalized)) {
            throw new BizException(40097, "PAGE_CAPABILITY_TARGET_KIND_INVALID");
        }
        return normalized;
    }

    private String required(String value, String error) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(40096, error);
        }
        return value.trim();
    }

    private String currentActor() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Short flag(Boolean value) {
        return (short) (Boolean.TRUE.equals(value) ? 1 : 0);
    }

    private boolean enabled(Short value) {
        return value != null && value == 1;
    }

    private <T> List<T> safe(List<T> values) {
        return values != null ? values : List.of();
    }

    private record Readiness(PageCapabilityReadiness status, String message) {
    }
}
