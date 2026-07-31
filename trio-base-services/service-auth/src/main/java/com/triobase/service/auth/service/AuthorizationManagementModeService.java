package com.triobase.service.auth.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.entity.SysAuthTenantManagementMode;
import com.triobase.service.auth.mapper.AuthTenantManagementModeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthorizationManagementModeService {

    private static final Set<String> MODES = Set.of("LEGACY", "MIGRATION", "PAGE_CAPABILITY");

    private final AuthTenantManagementModeMapper modeMapper;
    private final AuthorizationRegistryService registryService;
    private final AuthorizationCompatibilityService compatibilityService;

    public SysAuthTenantManagementMode current(String requestedTenantId) {
        String tenantId = registryService.effectiveTenant(requestedTenantId);
        SysAuthTenantManagementMode mode = modeMapper.selectById(tenantId);
        if (mode != null) return mode;
        SysAuthTenantManagementMode fallback = new SysAuthTenantManagementMode();
        fallback.setTenantId(tenantId);
        fallback.setManagementMode("LEGACY");
        return fallback;
    }

    @Transactional
    public SysAuthTenantManagementMode update(String requestedTenantId, String requestedMode) {
        String tenantId = registryService.effectiveTenant(requestedTenantId);
        String mode = StringUtils.hasText(requestedMode) ? requestedMode.trim().toUpperCase() : "";
        if (!MODES.contains(mode)) {
            throw new BizException(40099, "授权管理模式无效");
        }
        SysAuthTenantManagementMode existing = modeMapper.selectById(tenantId);
        if (existing != null && "PAGE_CAPABILITY".equals(existing.getManagementMode())
                && !"PAGE_CAPABILITY".equals(mode)) {
            throw new BizException(40998,
                    "租户已完成页面功能授权切换，不能重新开启旧授权写入");
        }
        if ("PAGE_CAPABILITY".equals(mode)) {
            var acceptance = compatibilityService.assess(tenantId);
            if (!acceptance.isCutoverReady()) {
                throw new BizException(40998,
                        "页面功能授权尚未通过生产验收：" + String.join("；", acceptance.getBlockers()));
            }
        }
        SysAuthTenantManagementMode entity = new SysAuthTenantManagementMode();
        entity.setTenantId(tenantId);
        entity.setManagementMode(mode);
        entity.setUpdatedBy(currentActor());
        entity.setUpdatedAt(LocalDateTime.now());
        if (existing == null) modeMapper.insert(entity);
        else modeMapper.updateById(entity);
        return entity;
    }

    public void requireLegacyWriteAllowed(String requestedTenantId) {
        if ("PAGE_CAPABILITY".equals(current(requestedTenantId).getManagementMode())) {
            throw new BizException(40999, "该租户已切换到页面功能授权，请在角色授权工作台修改并发布");
        }
    }

    public void requirePagePublicationAllowed(String requestedTenantId) {
        if ("LEGACY".equals(current(requestedTenantId).getManagementMode())) {
            throw new BizException(40999, "该租户仍使用旧授权模式，请先完成迁移复核再切换");
        }
    }

    private String currentActor() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }
}
