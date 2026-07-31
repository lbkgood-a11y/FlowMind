package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.auth.dto.LegacyRoleAuthorizationAnalysisResponse;
import com.triobase.service.auth.dto.ReplaceRoleCapabilityIntentRequest;
import com.triobase.service.auth.dto.RoleAuthorizationCompilationPlan;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCapabilityTarget;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.entity.SysRoleAuthDraft;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityTargetMapper;
import com.triobase.service.auth.mapper.AuthPageCatalogMapper;
import com.triobase.service.auth.mapper.RoleAuthDraftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LegacyRoleAuthorizationMigrationService {

    private final AuthGrantMapper grantMapper;
    private final AuthPageCatalogMapper catalogMapper;
    private final AuthPageCapabilityMapper capabilityMapper;
    private final AuthPageCapabilityTargetMapper targetMapper;
    private final AuthorizationRegistryService registryService;
    private final RolePageCapabilityStore capabilityStore;
    private final RoleAuthorizationCompiler compiler;
    private final RoleAuthorizationAuditService auditService;
    private final RoleAuthDraftMapper draftMapper;

    public LegacyRoleAuthorizationAnalysisResponse analyze(String requestedTenantId, String roleId) {
        return analyzeInternal(registryService.effectiveTenant(requestedTenantId), roleId, null, false);
    }

    @Transactional
    public LegacyRoleAuthorizationAnalysisResponse createReviewDraft(
            String requestedTenantId, String roleId) {
        String tenantId = registryService.effectiveTenant(requestedTenantId);
        LegacyRoleAuthorizationAnalysisResponse analysis = analyzeInternal(tenantId, roleId, null, false);
        SysRoleAuthDraft draft = capabilityStore.getOrCreateDraft(tenantId, roleId);
        Set<String> selectedIds = new HashSet<>();
        analysis.getEntries().stream()
                .filter(entry -> "EXACT".equals(entry.getResult()))
                .flatMap(entry -> entry.getCapabilityIds().stream())
                .forEach(selectedIds::add);
        ReplaceRoleCapabilityIntentRequest request = new ReplaceRoleCapabilityIntentRequest();
        request.setTenantId(tenantId);
        request.setExpectedVersion(draft.getIntentVersion());
        request.setSelections(selectedIds.stream().map(id -> {
            ReplaceRoleCapabilityIntentRequest.Selection selection =
                    new ReplaceRoleCapabilityIntentRequest.Selection();
            selection.setCapabilityId(id);
            selection.setSelectionSource("MIGRATION");
            return selection;
        }).toList());
        SysRoleAuthDraft updated = capabilityStore.replaceIntent(draft.getId(), request);
        RoleAuthorizationCompilationPlan plan = compiler.compile(tenantId, updated.getId());
        Set<String> legacyAllows = legacyAllows(tenantId, roleId);
        boolean expansion = plan.getGrants().stream()
                .map(item -> item.getResourceCode() + ":" + item.getActionCode())
                .anyMatch(item -> !legacyAllows.contains(item));
        updated.setMigrationReviewRequired((short) 1);
        updated.setMigrationExpansionDetected(expansion ? (short) 1 : (short) 0);
        draftMapper.updateById(updated);
        LegacyRoleAuthorizationAnalysisResponse result = analyzeInternal(
                tenantId, roleId, updated.getId(), expansion);
        auditService.record(tenantId, roleId, updated.getId(), null,
                "MIGRATION_ANALYZED",
                expansion
                        ? "旧权限已生成复核草稿，但检测到可能扩大权限，禁止直接发布"
                        : "旧权限已生成复核草稿，需实施人员确认后校验发布",
                result);
        return result;
    }

    private LegacyRoleAuthorizationAnalysisResponse analyzeInternal(
            String tenantId, String roleId, String draftId, boolean expansion) {
        SysAuthPageCatalog catalog = catalogMapper.selectOne(new LambdaQueryWrapper<SysAuthPageCatalog>()
                .eq(SysAuthPageCatalog::getTenantId, tenantId)
                .eq(SysAuthPageCatalog::getLifecycleStatus, "ACTIVE")
                .orderByDesc(SysAuthPageCatalog::getCatalogVersion)
                .last("LIMIT 1"));
        if (catalog == null) {
            return response(tenantId, roleId, draftId, expansion, List.of());
        }
        List<SysAuthPageCapability> capabilities = capabilityMapper.selectList(
                new LambdaQueryWrapper<SysAuthPageCapability>()
                        .eq(SysAuthPageCapability::getTenantId, tenantId)
                        .eq(SysAuthPageCapability::getCatalogId, catalog.getId())
                        .eq(SysAuthPageCapability::getStatus, (short) 1));
        Map<String, SysAuthPageCapability> capabilityById = new HashMap<>();
        capabilities.forEach(item -> capabilityById.put(item.getId(), item));
        Map<String, List<String>> capabilitiesByGrant = new HashMap<>();
        Map<String, Set<String>> targetsByCapability = new HashMap<>();
        if (!capabilities.isEmpty()) {
            targetMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapabilityTarget>()
                            .eq(SysAuthPageCapabilityTarget::getTenantId, tenantId)
                            .eq(SysAuthPageCapabilityTarget::getStatus, (short) 1)
                            .in(SysAuthPageCapabilityTarget::getCapabilityId, capabilityById.keySet()))
                    .forEach(target -> {
                        String key = target.getResourceCode() + ":" + target.getActionCode();
                        capabilitiesByGrant.computeIfAbsent(key, ignored -> new ArrayList<>())
                                .add(target.getCapabilityId());
                        targetsByCapability.computeIfAbsent(target.getCapabilityId(), ignored -> new HashSet<>())
                                .add(key);
                    });
        }
        Set<String> legacyAllows = legacyAllows(tenantId, roleId);
        List<LegacyRoleAuthorizationAnalysisResponse.Entry> entries = new ArrayList<>();
        for (String grant : legacyAllows) {
            int separator = grant.lastIndexOf(':');
            String resource = grant.substring(0, separator);
            String action = grant.substring(separator + 1);
            List<String> matches = capabilitiesByGrant.getOrDefault(grant, List.of());
            String result;
            String explanation;
            if (matches.isEmpty()) {
                result = "UNMAPPED";
                explanation = "没有页面功能能准确表达这项旧授权";
            } else if (matches.size() > 1) {
                result = "AMBIGUOUS";
                explanation = "多个页面功能都可能对应这项旧授权，需要人工选择";
            } else if (legacyAllows.containsAll(targetsByCapability.getOrDefault(matches.get(0), Set.of()))) {
                result = "EXACT";
                explanation = "可完整还原为一个页面功能";
            } else {
                result = "PARTIAL";
                explanation = "只能还原部分页面功能，自动选择可能扩大权限";
            }
            entries.add(LegacyRoleAuthorizationAnalysisResponse.Entry.builder()
                    .resourceCode(resource).actionCode(action).result(result)
                    .capabilityIds(matches)
                    .capabilityNames(matches.stream().map(capabilityById::get)
                            .filter(java.util.Objects::nonNull)
                            .map(SysAuthPageCapability::getCapabilityName).toList())
                    .explanation(explanation).build());
        }
        return response(tenantId, roleId, draftId, expansion, entries);
    }

    private Set<String> legacyAllows(String tenantId, String roleId) {
        Set<String> result = new HashSet<>();
        grantMapper.selectList(new LambdaQueryWrapper<SysAuthGrant>()
                        .eq(SysAuthGrant::getTenantId, tenantId)
                        .eq(SysAuthGrant::getSubjectType, "ROLE")
                        .eq(SysAuthGrant::getSubjectId, roleId)
                        .eq(SysAuthGrant::getEffect, "ALLOW")
                        .eq(SysAuthGrant::getStatus, (short) 1))
                .forEach(grant -> result.add(grant.getResourceCode() + ":" + grant.getActionCode()));
        return result;
    }

    private LegacyRoleAuthorizationAnalysisResponse response(
            String tenantId, String roleId, String draftId, boolean expansion,
            List<LegacyRoleAuthorizationAnalysisResponse.Entry> entries) {
        Map<String, Long> counts = entries.stream().collect(java.util.stream.Collectors.groupingBy(
                LegacyRoleAuthorizationAnalysisResponse.Entry::getResult,
                LinkedHashMap::new, java.util.stream.Collectors.counting()));
        return LegacyRoleAuthorizationAnalysisResponse.builder()
                .tenantId(tenantId).roleId(roleId).draftId(draftId)
                .exactCount(counts.getOrDefault("EXACT", 0L))
                .partialCount(counts.getOrDefault("PARTIAL", 0L))
                .ambiguousCount(counts.getOrDefault("AMBIGUOUS", 0L))
                .unmappedCount(counts.getOrDefault("UNMAPPED", 0L))
                .reviewRequired(true).permissionExpansionDetected(expansion)
                .entries(entries).build();
    }
}
