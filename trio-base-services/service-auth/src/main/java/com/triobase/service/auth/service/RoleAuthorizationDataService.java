package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.auth.entity.SysAuthFieldPolicy;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysDataPolicy;
import com.triobase.service.auth.entity.SysMenu;
import com.triobase.service.auth.mapper.AuthFieldPolicyMapper;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.DataPolicyMapper;
import com.triobase.service.auth.mapper.MenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleAuthorizationDataService {

    private static final String DEFAULT_TENANT = "default";
    private static final String SUBJECT_ROLE = "ROLE";
    private static final String EFFECT_ALLOW = "ALLOW";
    private static final short STATUS_ENABLED = 1;

    private final MenuMapper menuMapper;
    private final AuthGrantMapper grantMapper;
    private final AuthFieldPolicyMapper fieldPolicyMapper;
    private final DataPolicyMapper dataPolicyMapper;
    private final AuthorizationVersionService versionService;

    public List<String> menuIdsForRole(String roleId) {
        String normalizedRoleId = normalizeBlank(roleId);
        if (normalizedRoleId == null) {
            return List.of();
        }
        List<SysMenu> allMenus = menuMapper.selectList(null);
        List<SysAuthGrant> grants = grantMapper.selectList(new LambdaQueryWrapper<SysAuthGrant>()
                        .eq(SysAuthGrant::getTenantId, DEFAULT_TENANT)
                        .eq(SysAuthGrant::getSubjectType, SUBJECT_ROLE)
                        .eq(SysAuthGrant::getSubjectId, normalizedRoleId)
                        .eq(SysAuthGrant::getStatus, STATUS_ENABLED));
        Set<PermissionKey> deniedPermissions = grants.stream()
                .filter(grant -> "DENY".equalsIgnoreCase(grant.getEffect()))
                .map(grant -> new PermissionKey(grant.getResourceCode(), grant.getActionCode()))
                .collect(Collectors.toSet());
        Set<PermissionKey> grantedPermissions = grants.stream()
                .filter(grant -> EFFECT_ALLOW.equalsIgnoreCase(grant.getEffect()))
                .map(grant -> new PermissionKey(grant.getResourceCode(), grant.getActionCode()))
                .collect(Collectors.toSet());
        if (grantedPermissions.isEmpty()) {
            return List.of();
        }

        Set<String> selectedMenuIds = allMenus.stream()
                .filter(menu -> {
                    PermissionKey key = resolvePermissionKey(menu);
                    return key != null && grantedPermissions.contains(key) && !deniedPermissions.contains(key);
                })
                .map(SysMenu::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        includeAncestorMenus(allMenus, selectedMenuIds);
        return List.copyOf(selectedMenuIds);
    }

    @Transactional
    public void deleteRoleAuthorizationData(String roleId) {
        String normalizedRoleId = normalizeBlank(roleId);
        if (normalizedRoleId == null) {
            return;
        }
        boolean grantChanged = grantMapper.delete(new LambdaQueryWrapper<SysAuthGrant>()
                .eq(SysAuthGrant::getSubjectType, SUBJECT_ROLE)
                .eq(SysAuthGrant::getSubjectId, normalizedRoleId)) > 0;
        boolean fieldPolicyChanged = fieldPolicyMapper.delete(new LambdaQueryWrapper<SysAuthFieldPolicy>()
                .eq(SysAuthFieldPolicy::getSubjectType, SUBJECT_ROLE)
                .eq(SysAuthFieldPolicy::getSubjectId, normalizedRoleId)) > 0;
        boolean dataPolicyChanged = dataPolicyMapper.delete(new LambdaQueryWrapper<SysDataPolicy>()
                .eq(SysDataPolicy::getSubjectType, SUBJECT_ROLE)
                .eq(SysDataPolicy::getSubjectId, normalizedRoleId)) > 0;
        bumpVersions(false, grantChanged, fieldPolicyChanged, dataPolicyChanged);
    }

    private PermissionKey resolvePermissionKey(SysMenu menu) {
        String permissionCode = normalizeBlank(menu.getPermissionCode());
        if (permissionCode != null) {
            return parsePermissionCode(permissionCode);
        }
        return null;
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

    private void bumpVersions(boolean resourceChanged,
                              boolean grantChanged,
                              boolean fieldPolicyChanged,
                              boolean dataPolicyChanged) {
        boolean authChanged = resourceChanged || grantChanged || fieldPolicyChanged || dataPolicyChanged;
        if (resourceChanged) {
            versionService.bump(AuthorizationVersionService.RESOURCE);
        }
        if (grantChanged) {
            versionService.bump(AuthorizationVersionService.GRANT);
        }
        if (fieldPolicyChanged) {
            versionService.bump(AuthorizationVersionService.FIELD_POLICY);
        }
        if (dataPolicyChanged) {
            versionService.bump(AuthorizationVersionService.DATA_POLICY);
        }
        if (authChanged) {
            versionService.bump(AuthorizationVersionService.AUTHORIZATION);
        }
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void includeAncestorMenus(List<SysMenu> menus, Set<String> menuIds) {
        if (menuIds.isEmpty()) {
            return;
        }
        Map<String, SysMenu> menuById = menus.stream()
                .collect(Collectors.toMap(SysMenu::getId, menu -> menu, (left, right) -> left));
        Set<String> selectedIds = new HashSet<>(menuIds);
        for (String selectedId : selectedIds) {
            SysMenu current = menuById.get(selectedId);
            while (current != null && StringUtils.hasText(current.getParentId())) {
                String parentId = current.getParentId();
                if (!menuIds.add(parentId)) {
                    break;
                }
                current = menuById.get(parentId);
            }
        }
    }

    private record PermissionKey(String resourceCode, String actionCode) {
    }
}
