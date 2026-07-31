package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.auth.entity.SysRoleAuthActiveRelease;
import com.triobase.service.auth.entity.SysRoleAuthCompiledEvidence;
import com.triobase.service.auth.mapper.RoleAuthActiveReleaseMapper;
import com.triobase.service.auth.mapper.RoleAuthCompiledEvidenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActiveReleaseEvidenceService {

    private final AuthorizationManagementModeService modeService;
    private final RoleAuthActiveReleaseMapper activeReleaseMapper;
    private final RoleAuthCompiledEvidenceMapper evidenceMapper;

    public boolean isPageCapabilityMode(String tenantId) {
        return "PAGE_CAPABILITY".equals(modeService.current(tenantId).getManagementMode());
    }

    public boolean hasAnyGrantEvidence(String tenantId, String roleId) {
        if (!isPageCapabilityMode(tenantId)) return true;
        SysRoleAuthActiveRelease active = active(tenantId, roleId);
        if (active == null) return false;
        Long count = evidenceMapper.selectCount(new LambdaQueryWrapper<SysRoleAuthCompiledEvidence>()
                .eq(SysRoleAuthCompiledEvidence::getTenantId, tenantId)
                .eq(SysRoleAuthCompiledEvidence::getReleaseId, active.getReleaseId())
                .eq(SysRoleAuthCompiledEvidence::getProjectionType, "GRANT"));
        return count != null && count > 0;
    }

    /**
     * Batch pre-loads all GRANT evidence for the given roles in two queries.
     * Returns null for non-PAGE_CAPABILITY tenants (meaning "allow all").
     * Returns an empty set when no active release exists for any role.
     */
    public Set<String> batchSupportedGrantKeys(String tenantId, List<String> roleIds) {
        if (!isPageCapabilityMode(tenantId)) return null;
        if (roleIds == null || roleIds.isEmpty()) return Collections.emptySet();

        List<SysRoleAuthActiveRelease> actives = activeReleaseMapper.selectList(
                new LambdaQueryWrapper<SysRoleAuthActiveRelease>()
                        .eq(SysRoleAuthActiveRelease::getTenantId, tenantId)
                        .in(SysRoleAuthActiveRelease::getRoleId, roleIds));
        if (actives.isEmpty()) return Collections.emptySet();

        Map<String, String> roleByRelease = new HashMap<>();
        for (SysRoleAuthActiveRelease a : actives) {
            roleByRelease.put(a.getReleaseId(), a.getRoleId());
        }

        List<String> releaseIds = actives.stream()
                .map(SysRoleAuthActiveRelease::getReleaseId).distinct().toList();

        List<SysRoleAuthCompiledEvidence> evidences = evidenceMapper.selectList(
                new LambdaQueryWrapper<SysRoleAuthCompiledEvidence>()
                        .eq(SysRoleAuthCompiledEvidence::getTenantId, tenantId)
                        .in(SysRoleAuthCompiledEvidence::getReleaseId, releaseIds)
                        .eq(SysRoleAuthCompiledEvidence::getProjectionType, "GRANT"));

        return evidences.stream()
                .map(e -> roleByRelease.get(e.getReleaseId()) + ":" + e.getResourceCode() + ":" + e.getActionCode())
                .collect(Collectors.toSet());
    }

    public boolean supportsGrant(String tenantId, String roleId,
                                 String resourceCode, String actionCode) {
        if (!isPageCapabilityMode(tenantId)) return true;
        SysRoleAuthActiveRelease active = active(tenantId, roleId);
        if (active == null) return false;
        Long count = evidenceMapper.selectCount(new LambdaQueryWrapper<SysRoleAuthCompiledEvidence>()
                .eq(SysRoleAuthCompiledEvidence::getTenantId, tenantId)
                .eq(SysRoleAuthCompiledEvidence::getReleaseId, active.getReleaseId())
                .eq(SysRoleAuthCompiledEvidence::getProjectionType, "GRANT")
                .eq(SysRoleAuthCompiledEvidence::getResourceCode, resourceCode)
                .eq(SysRoleAuthCompiledEvidence::getActionCode, actionCode));
        return count != null && count > 0;
    }

    private SysRoleAuthActiveRelease active(String tenantId, String roleId) {
        return activeReleaseMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthActiveRelease>()
                .eq(SysRoleAuthActiveRelease::getTenantId, tenantId)
                .eq(SysRoleAuthActiveRelease::getRoleId, roleId));
    }
}
