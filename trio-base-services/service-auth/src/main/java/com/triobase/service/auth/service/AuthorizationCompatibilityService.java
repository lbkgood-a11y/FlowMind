package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.auth.dto.AuthorizationCompatibilityDashboardResponse;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysRoleAuthActiveRelease;
import com.triobase.service.auth.entity.SysRoleAuthAudit;
import com.triobase.service.auth.entity.SysRoleAuthCompiledEvidence;
import com.triobase.service.auth.entity.SysRoleAuthDraft;
import com.triobase.service.auth.entity.SysRoleAuthDrift;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCatalogMapper;
import com.triobase.service.auth.mapper.RoleAuthActiveReleaseMapper;
import com.triobase.service.auth.mapper.RoleAuthAuditMapper;
import com.triobase.service.auth.mapper.RoleAuthCompiledEvidenceMapper;
import com.triobase.service.auth.mapper.RoleAuthDraftMapper;
import com.triobase.service.auth.mapper.RoleAuthDriftMapper;
import com.triobase.service.auth.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Produces cutover evidence from persisted runtime state. The result is not a
 * manually editable checklist: every number is recalculated from the active
 * catalog, active releases and the projections currently enforced at runtime.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationCompatibilityService {

    private static final int ROLE_DETAIL_LIMIT = 500;

    private final AuthorizationRegistryService registryService;
    private final AuthPageCatalogMapper catalogMapper;
    private final AuthPageCapabilityMapper capabilityMapper;
    private final RoleMapper roleMapper;
    private final RoleAuthActiveReleaseMapper activeReleaseMapper;
    private final RoleAuthCompiledEvidenceMapper evidenceMapper;
    private final AuthGrantMapper grantMapper;
    private final RoleAuthDraftMapper draftMapper;
    private final RoleAuthDriftMapper driftMapper;
    private final RoleAuthAuditMapper auditMapper;
    private final LowcodeOwnershipDiagnosticsClient lowcodeOwnershipDiagnosticsClient;

    public AuthorizationCompatibilityDashboardResponse assess(String requestedTenantId) {
        String tenantId = registryService.effectiveTenant(requestedTenantId);
        int queryCount = 0;
        SysAuthPageCatalog catalog = activeCatalog(tenantId);
        queryCount++;
        List<SysAuthPageCapability> capabilities = catalog == null ? List.of()
                : capabilityMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapability>()
                .eq(SysAuthPageCapability::getTenantId, tenantId)
                .eq(SysAuthPageCapability::getCatalogId, catalog.getId())
                .eq(SysAuthPageCapability::getStatus, (short) 1));
        if (catalog != null) queryCount++;
        long readyCapabilities = capabilities.stream()
                .filter(item -> "READY".equals(item.getReadinessStatus())).count();

        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)
                .eq(SysRole::getStatus, (short) 1));
        queryCount++;
        Map<String, SysRoleAuthActiveRelease> activeByRole = new HashMap<>();
        activeReleaseMapper.selectList(new LambdaQueryWrapper<SysRoleAuthActiveRelease>()
                        .eq(SysRoleAuthActiveRelease::getTenantId, tenantId))
                .forEach(item -> activeByRole.put(item.getRoleId(), item));
        queryCount++;

        Set<String> releaseIds = activeByRole.values().stream()
                .map(SysRoleAuthActiveRelease::getReleaseId)
                .collect(Collectors.toSet());
        Map<String, Set<String>> expectedByRelease = releaseIds.isEmpty() ? Map.of()
                : evidenceMapper.selectList(new LambdaQueryWrapper<SysRoleAuthCompiledEvidence>()
                        .eq(SysRoleAuthCompiledEvidence::getTenantId, tenantId)
                        .in(SysRoleAuthCompiledEvidence::getReleaseId, releaseIds)
                        .eq(SysRoleAuthCompiledEvidence::getProjectionType, "GRANT"))
                .stream().collect(Collectors.groupingBy(
                        SysRoleAuthCompiledEvidence::getReleaseId,
                        Collectors.mapping(this::evidenceKey, Collectors.toSet())));
        if (!releaseIds.isEmpty()) queryCount++;
        Set<String> roleIds = roles.stream().map(SysRole::getId).collect(Collectors.toSet());
        Map<String, Set<String>> actualByRole = roleIds.isEmpty() ? Map.of()
                : grantMapper.selectList(new LambdaQueryWrapper<SysAuthGrant>()
                        .eq(SysAuthGrant::getTenantId, tenantId)
                        .eq(SysAuthGrant::getSubjectType, "ROLE")
                        .in(SysAuthGrant::getSubjectId, roleIds)
                        .eq(SysAuthGrant::getStatus, (short) 1))
                .stream().collect(Collectors.groupingBy(
                        SysAuthGrant::getSubjectId,
                        Collectors.mapping(this::grantKey, Collectors.toSet())));
        if (!roleIds.isEmpty()) queryCount++;

        long equivalentRoles = 0;
        long mismatchRoles = 0;
        long missingProjections = 0;
        long unintendedExpansions = 0;
        List<AuthorizationCompatibilityDashboardResponse.RoleStatus> roleStatuses = new ArrayList<>();
        for (SysRole role : roles) {
            SysRoleAuthActiveRelease active = activeByRole.get(role.getId());
            if (active == null) {
                roleStatuses.add(roleStatus(role, "PENDING_MIGRATION", 0, 0));
                continue;
            }
            Set<String> expected = expectedByRelease.getOrDefault(active.getReleaseId(), Set.of());
            Set<String> actual = actualByRole.getOrDefault(role.getId(), Set.of());
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(actual);
            Set<String> expanded = new HashSet<>(actual);
            expanded.removeAll(expected);
            missingProjections += missing.size();
            unintendedExpansions += expanded.size();
            if (missing.isEmpty() && expanded.isEmpty()) {
                equivalentRoles++;
                roleStatuses.add(roleStatus(role, "EQUIVALENT", 0, 0));
            } else {
                mismatchRoles++;
                roleStatuses.add(roleStatus(role, "MISMATCH", missing.size(), expanded.size()));
            }
        }

        long publishedRoles = roles.stream().filter(role -> activeByRole.containsKey(role.getId())).count();
        long unresolvedExpansionReviews = draftMapper.selectCount(
                new LambdaQueryWrapper<SysRoleAuthDraft>()
                        .eq(SysRoleAuthDraft::getTenantId, tenantId)
                        .eq(SysRoleAuthDraft::getMigrationExpansionDetected, (short) 1)
                        .eq(SysRoleAuthDraft::getMigrationExpansionAcknowledged, (short) 0));
        queryCount++;
        long openDrifts = driftMapper.selectCount(new LambdaQueryWrapper<SysRoleAuthDrift>()
                .eq(SysRoleAuthDrift::getTenantId, tenantId)
                .eq(SysRoleAuthDrift::getDriftStatus, "OPEN"));
        queryCount++;
        long publishFailures = auditCount(tenantId, "PUBLISH_FAILED");
        long rollbacks = auditCount(tenantId, "ROLLED_BACK");
        queryCount += 2;
        long unresolvedOwnership = 0;
        boolean ownershipDiagnosticsAvailable = true;
        try {
            unresolvedOwnership = lowcodeOwnershipDiagnosticsClient.unresolvedCount(tenantId);
        } catch (RuntimeException exception) {
            ownershipDiagnosticsAvailable = false;
        }

        List<String> blockers = new ArrayList<>();
        if (catalog == null) blockers.add("尚未激活页面功能目录");
        if (capabilities.isEmpty()) blockers.add("页面功能目录中没有可验收功能");
        if (readyCapabilities != capabilities.size()) {
            blockers.add("有 " + (capabilities.size() - readyCapabilities) + " 个页面功能尚未完成后台验证");
        }
        if (publishedRoles != roles.size()) {
            blockers.add("有 " + (roles.size() - publishedRoles) + " 个启用角色尚未发布页面功能授权");
        }
        if (missingProjections > 0) blockers.add("运行时缺少 " + missingProjections + " 项已发布权限");
        if (unintendedExpansions > 0) blockers.add("运行时存在 " + unintendedExpansions + " 项发布版本之外的权限");
        if (unresolvedExpansionReviews > 0) blockers.add("有 " + unresolvedExpansionReviews + " 份扩权风险尚未确认");
        if (openDrifts > 0) blockers.add("有 " + openDrifts + " 项映射变化尚未处理");
        if (!ownershipDiagnosticsAvailable) blockers.add("低代码组织归属诊断不可用");
        if (unresolvedOwnership > 0) {
            blockers.add("仍有 " + unresolvedOwnership + " 条低代码记录缺少可信组织归属");
        }

        return AuthorizationCompatibilityDashboardResponse.builder()
                .tenantId(tenantId)
                .catalogCapabilityCount(capabilities.size())
                .catalogReadyCount(readyCapabilities)
                .catalogNotReadyCount(capabilities.size() - readyCapabilities)
                .totalRoleCount(roles.size())
                .publishedRoleCount(publishedRoles)
                .pendingMigrationRoleCount(roles.size() - publishedRoles)
                .decisionEquivalentRoleCount(equivalentRoles)
                .decisionMismatchRoleCount(mismatchRoles)
                .missingProjectionCount(missingProjections)
                .unintendedExpansionCount(unintendedExpansions)
                .unresolvedExpansionReviewCount(unresolvedExpansionReviews)
                .openDriftCount(openDrifts)
                .publicationFailureCount(publishFailures)
                .rollbackCount(rollbacks)
                .compatibilityQueryCount(queryCount)
                .roleDetailLimit(ROLE_DETAIL_LIMIT)
                .roleDetailsTruncated(roleStatuses.size() > ROLE_DETAIL_LIMIT)
                .lowcodeUnresolvedOwnershipCount(unresolvedOwnership)
                .lowcodeOwnershipDiagnosticsAvailable(ownershipDiagnosticsAvailable)
                .cutoverReady(blockers.isEmpty())
                .blockers(List.copyOf(blockers))
                .roleStatuses(List.copyOf(roleStatuses.stream().limit(ROLE_DETAIL_LIMIT).toList()))
                .build();
    }

    private SysAuthPageCatalog activeCatalog(String tenantId) {
        return catalogMapper.selectOne(new LambdaQueryWrapper<SysAuthPageCatalog>()
                .eq(SysAuthPageCatalog::getTenantId, tenantId)
                .eq(SysAuthPageCatalog::getLifecycleStatus, "ACTIVE")
                .orderByDesc(SysAuthPageCatalog::getCatalogVersion)
                .last("LIMIT 1"));
    }

    private long auditCount(String tenantId, String eventType) {
        return auditMapper.selectCount(new LambdaQueryWrapper<SysRoleAuthAudit>()
                .eq(SysRoleAuthAudit::getTenantId, tenantId)
                .eq(SysRoleAuthAudit::getEventType, eventType));
    }

    private String evidenceKey(SysRoleAuthCompiledEvidence item) {
        return item.getResourceCode() + ":" + item.getActionCode() + ":" + item.getEffect();
    }

    private String grantKey(SysAuthGrant item) {
        return item.getResourceCode() + ":" + item.getActionCode() + ":" + item.getEffect();
    }

    private AuthorizationCompatibilityDashboardResponse.RoleStatus roleStatus(
            SysRole role, String status, long missing, long expansion) {
        return AuthorizationCompatibilityDashboardResponse.RoleStatus.builder()
                .roleId(role.getId()).roleName(role.getRoleName()).status(status)
                .missingProjectionCount(missing).unintendedExpansionCount(expansion).build();
    }
}
