package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.auth.dto.RoleAuthorizationDriftResponse;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCapabilityTarget;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.entity.SysRoleAuthActiveRelease;
import com.triobase.service.auth.entity.SysRoleAuthCompiledEvidence;
import com.triobase.service.auth.entity.SysRoleAuthDrift;
import com.triobase.service.auth.entity.SysRoleAuthRelease;
import com.triobase.service.auth.entity.SysUserRole;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityTargetMapper;
import com.triobase.service.auth.mapper.RoleAuthActiveReleaseMapper;
import com.triobase.service.auth.mapper.RoleAuthCompiledEvidenceMapper;
import com.triobase.service.auth.mapper.RoleAuthDriftMapper;
import com.triobase.service.auth.mapper.RoleAuthReleaseMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleAuthorizationDriftService {

    private final RoleAuthActiveReleaseMapper activeReleaseMapper;
    private final RoleAuthReleaseMapper releaseMapper;
    private final RoleAuthCompiledEvidenceMapper evidenceMapper;
    private final RoleAuthDriftMapper driftMapper;
    private final AuthPageCapabilityMapper capabilityMapper;
    private final AuthPageCapabilityTargetMapper targetMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthorizationRegistryService registryService;
    private final RoleAuthorizationAuditService auditService;

    @Transactional
    public int detectForActivatedCatalog(SysAuthPageCatalog catalog) {
        List<SysAuthPageCapability> newCapabilities = capabilityMapper.selectList(
                new LambdaQueryWrapper<SysAuthPageCapability>()
                        .eq(SysAuthPageCapability::getTenantId, catalog.getTenantId())
                        .eq(SysAuthPageCapability::getCatalogId, catalog.getId())
                        .eq(SysAuthPageCapability::getStatus, (short) 1));
        Map<String, SysAuthPageCapability> newByCode = new HashMap<>();
        newCapabilities.forEach(item -> newByCode.put(item.getCapabilityCode(), item));
        Map<String, Set<String>> newTargets = targetsByCapability(catalog.getTenantId(), newCapabilities);
        int detected = 0;
        List<SysRoleAuthActiveRelease> activeReleases = activeReleaseMapper.selectList(
                new LambdaQueryWrapper<SysRoleAuthActiveRelease>()
                        .eq(SysRoleAuthActiveRelease::getTenantId, catalog.getTenantId()));
        for (SysRoleAuthActiveRelease pointer : activeReleases) {
            SysRoleAuthRelease release = releaseMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthRelease>()
                    .eq(SysRoleAuthRelease::getTenantId, catalog.getTenantId())
                    .eq(SysRoleAuthRelease::getId, pointer.getReleaseId()));
            if (release == null || release.getCatalogVersion().equals(catalog.getCatalogVersion())) {
                continue;
            }
            Map<String, Set<String>> oldTargets = oldTargets(catalog.getTenantId(), release.getId());
            for (Map.Entry<String, Set<String>> old : oldTargets.entrySet()) {
                SysAuthPageCapability current = newByCode.get(old.getKey());
                String type;
                Set<String> currentTargets;
                if (current == null) {
                    type = "CAPABILITY_REMOVED";
                    currentTargets = Set.of();
                } else {
                    currentTargets = newTargets.getOrDefault(current.getId(), Set.of());
                    if (old.getValue().equals(currentTargets)) continue;
                    if (currentTargets.containsAll(old.getValue())) type = "TARGET_ADDED";
                    else if (old.getValue().containsAll(currentTargets)) type = "TARGET_REMOVED";
                    else type = "TARGET_CHANGED";
                }
                long users = affectedUsers(pointer.getRoleId());
                SysRoleAuthDrift drift = new SysRoleAuthDrift();
                drift.setTenantId(catalog.getTenantId());
                drift.setRoleId(pointer.getRoleId());
                drift.setReleaseId(release.getId());
                drift.setCapabilityCode(old.getKey());
                drift.setOldCatalogVersion(release.getCatalogVersion());
                drift.setNewCatalogVersion(catalog.getCatalogVersion());
                drift.setDriftType(type);
                drift.setDriftStatus("OPEN");
                drift.setAffectedUserCount(users);
                drift.setImpactSummary(summary(type, old.getKey(), users));
                drift.setDetectedAt(LocalDateTime.now());
                driftMapper.insert(drift);
                auditService.record(catalog.getTenantId(), pointer.getRoleId(), null, release.getId(),
                        "DRIFT_DETECTED", drift.getImpactSummary(), Map.of(
                                "oldTargets", old.getValue(), "newTargets", currentTargets,
                                "newCatalogVersion", catalog.getCatalogVersion()));
                detected++;
            }
        }
        return detected;
    }

    public List<RoleAuthorizationDriftResponse> openDrifts(String requestedTenantId, String roleId) {
        String tenantId = registryService.effectiveTenant(requestedTenantId);
        LambdaQueryWrapper<SysRoleAuthDrift> query = new LambdaQueryWrapper<SysRoleAuthDrift>()
                .eq(SysRoleAuthDrift::getTenantId, tenantId)
                .eq(SysRoleAuthDrift::getDriftStatus, "OPEN")
                .orderByDesc(SysRoleAuthDrift::getDetectedAt);
        if (roleId != null && !roleId.isBlank()) query.eq(SysRoleAuthDrift::getRoleId, roleId.trim());
        return driftMapper.selectList(query).stream().map(RoleAuthorizationDriftResponse::from).toList();
    }

    private Map<String, Set<String>> targetsByCapability(
            String tenantId, List<SysAuthPageCapability> capabilities) {
        Map<String, Set<String>> result = new HashMap<>();
        if (capabilities.isEmpty()) return result;
        targetMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapabilityTarget>()
                        .eq(SysAuthPageCapabilityTarget::getTenantId, tenantId)
                        .eq(SysAuthPageCapabilityTarget::getStatus, (short) 1)
                        .in(SysAuthPageCapabilityTarget::getCapabilityId,
                                capabilities.stream().map(SysAuthPageCapability::getId).toList()))
                .forEach(item -> result.computeIfAbsent(item.getCapabilityId(), ignored -> new HashSet<>())
                        .add(item.getResourceCode() + ":" + item.getActionCode()));
        return result;
    }

    private Map<String, Set<String>> oldTargets(String tenantId, String releaseId) {
        Map<String, Set<String>> result = new HashMap<>();
        evidenceMapper.selectList(new LambdaQueryWrapper<SysRoleAuthCompiledEvidence>()
                        .eq(SysRoleAuthCompiledEvidence::getTenantId, tenantId)
                        .eq(SysRoleAuthCompiledEvidence::getReleaseId, releaseId)
                        .eq(SysRoleAuthCompiledEvidence::getProjectionType, "GRANT"))
                .forEach(item -> result.computeIfAbsent(item.getCapabilityCode(), ignored -> new HashSet<>())
                        .add(item.getResourceCode() + ":" + item.getActionCode()));
        return result;
    }

    private long affectedUsers(String roleId) {
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        return count == null ? 0L : count;
    }

    private String summary(String type, String capabilityCode, long users) {
        String change = switch (type) {
            case "CAPABILITY_REMOVED" -> "页面功能已移除";
            case "TARGET_ADDED" -> "页面功能连接了新的后台能力";
            case "TARGET_REMOVED" -> "页面功能减少了后台能力";
            default -> "页面功能连接关系已变化";
        };
        return change + "，涉及 " + users + " 名用户；当前线上权限保持不变，需人工复核后重新发布（" + capabilityCode + "）";
    }
}
