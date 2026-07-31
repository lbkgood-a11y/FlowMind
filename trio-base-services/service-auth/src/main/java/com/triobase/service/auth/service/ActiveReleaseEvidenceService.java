package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.auth.entity.SysRoleAuthActiveRelease;
import com.triobase.service.auth.entity.SysRoleAuthCompiledEvidence;
import com.triobase.service.auth.mapper.RoleAuthActiveReleaseMapper;
import com.triobase.service.auth.mapper.RoleAuthCompiledEvidenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
