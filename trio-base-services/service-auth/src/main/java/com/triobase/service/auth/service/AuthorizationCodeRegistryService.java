package com.triobase.service.auth.service;

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

    private static final String DEFAULT_TENANT = "default";
    private static final String ACTIVE = "ACTIVE";
    private static final short STATUS_ENABLED = 1;

    private final AuthResourceMapper resourceMapper;
    private final AuthActionMapper actionMapper;

    public List<String> missingRegisteredCodes(List<String> codes) {
        Set<String> requested = normalizeCodes(codes);
        if (requested.isEmpty()) {
            return List.of();
        }
        return requested.stream()
                .filter(code -> !isRegistered(code))
                .toList();
    }

    private boolean isRegistered(String code) {
        PermissionKey key = parsePermissionCode(code);
        if (key == null) {
            return false;
        }
        Long resourceCount = resourceMapper.selectCount(new LambdaQueryWrapper<SysAuthResource>()
                .eq(SysAuthResource::getTenantId, DEFAULT_TENANT)
                .eq(SysAuthResource::getResourceCode, key.resourceCode())
                .eq(SysAuthResource::getLifecycleStatus, ACTIVE));
        Long actionCount = actionMapper.selectCount(new LambdaQueryWrapper<SysAuthAction>()
                .eq(SysAuthAction::getTenantId, DEFAULT_TENANT)
                .eq(SysAuthAction::getResourceCode, key.resourceCode())
                .eq(SysAuthAction::getActionCode, key.actionCode())
                .eq(SysAuthAction::getStatus, STATUS_ENABLED));
        return resourceCount != null && resourceCount > 0 && actionCount != null && actionCount > 0;
    }

    private PermissionKey parsePermissionCode(String permissionCode) {
        int separator = permissionCode.lastIndexOf(':');
        if (separator <= 0 || separator >= permissionCode.length() - 1) {
            return null;
        }
        String resourceCode = normalizeBlank(permissionCode.substring(0, separator));
        String actionCode = normalizeBlank(permissionCode.substring(separator + 1));
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

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record PermissionKey(String resourceCode, String actionCode) {
    }
}
