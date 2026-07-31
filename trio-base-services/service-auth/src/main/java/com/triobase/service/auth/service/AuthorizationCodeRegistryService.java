package com.triobase.service.auth.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.util.StringHelpers;
import com.triobase.common.core.exception.BizException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.auth.entity.SysAuthAction;
import com.triobase.service.auth.entity.SysAuthResource;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthorizationCodeRegistryService {

    private static final String ACTIVE = "ACTIVE";
    private static final short STATUS_ENABLED = 1;

    private final AuthResourceMapper resourceMapper;
    private final AuthActionMapper actionMapper;

    public List<String> missingRegisteredCodes(List<String> codes) {
        return missingRegisteredCodes(currentTenantId(), codes);
    }

    public List<String> missingRegisteredCodes(String tenantId, List<String> codes) {
        Set<String> requested = normalizeCodes(codes);
        if (requested.isEmpty()) {
            return List.of();
        }
        String effectiveTenant = requiredTenant(tenantId);
        return requested.stream()
                .filter(code -> !isRegistered(effectiveTenant, code))
                .toList();
    }

    private boolean isRegistered(String tenantId, String code) {
        PermissionKey key = parsePermissionCode(code);
        if (key == null) {
            return false;
        }
        Long resourceCount = resourceMapper.selectCount(new LambdaQueryWrapper<SysAuthResource>()
                .eq(SysAuthResource::getTenantId, tenantId)
                .eq(SysAuthResource::getResourceCode, key.resourceCode())
                .eq(SysAuthResource::getLifecycleStatus, ACTIVE));
        Long actionCount = actionMapper.selectCount(new LambdaQueryWrapper<SysAuthAction>()
                .eq(SysAuthAction::getTenantId, tenantId)
                .eq(SysAuthAction::getResourceCode, key.resourceCode())
                .eq(SysAuthAction::getActionCode, key.actionCode())
                .eq(SysAuthAction::getStatus, STATUS_ENABLED));
        return resourceCount != null && resourceCount > 0 && actionCount != null && actionCount > 0;
    }

    private String currentTenantId() {
        return requiredTenant(SecurityContextHolder.getTenantId());
    }

    private String requiredTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(40082, "AUTHZ_TENANT_REQUIRED");
        }
        return tenantId.trim();
    }

    private PermissionKey parsePermissionCode(String permissionCode) {
        int separator = permissionCode.lastIndexOf(':');
        if (separator <= 0 || separator >= permissionCode.length() - 1) {
            return null;
        }
        String resourceCode = StringHelpers.normalizeBlank(permissionCode.substring(0, separator));
        String actionCode = StringHelpers.normalizeBlank(permissionCode.substring(separator + 1));
        return resourceCode != null && actionCode != null
                ? new PermissionKey(resourceCode, actionCode)
                : null;
    }

    private Set<String> normalizeCodes(List<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        return codes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(TreeSet::new));
    }


    private record PermissionKey(String resourceCode, String actionCode) {
    }
}
