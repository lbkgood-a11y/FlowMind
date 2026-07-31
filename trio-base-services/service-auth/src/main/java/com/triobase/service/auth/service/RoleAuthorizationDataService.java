package com.triobase.service.auth.service;

import com.triobase.common.core.util.StringHelpers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.auth.entity.SysAuthFieldPolicy;
import com.triobase.service.auth.dto.RoleMenuProjectionResponse;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleAuthorizationDataService {

    private static final String SUBJECT_ROLE = "ROLE";
    private static final String EFFECT_ALLOW = "ALLOW";
    private static final short STATUS_ENABLED = 1;

    private final MenuMapper menuMapper;
    private final AuthGrantMapper grantMapper;
    private final AuthFieldPolicyMapper fieldPolicyMapper;
    private final DataPolicyMapper dataPolicyMapper;
    private final AuthorizationVersionService versionService;
    private final ActiveReleaseEvidenceService activeReleaseEvidenceService;

    public List<String> menuIdsForRole(String roleId) {
        return menuProjectionForRole(currentTenantId(), roleId).stream()
                .map(RoleMenuProjectionResponse::getMenuId)
                .toList();
    }

    public List<RoleMenuProjectionResponse> menuProjectionForRole(String roleId) {
        return menuProjectionForRole(currentTenantId(), roleId);
    }

    public List<RoleMenuProjectionResponse> menuProjectionForRole(String tenantId, String roleId) {
        String normalizedTenantId = requiredTenant(tenantId);
        String normalizedRoleId = StringHelpers.normalizeBlank(roleId);
        if (normalizedRoleId == null) {
            return List.of();
        }
        if (!activeReleaseEvidenceService.hasAnyGrantEvidence(normalizedTenantId, normalizedRoleId)) {
            return List.of();
        }
        List<SysMenu> allMenus = menuMapper.selectList(null);
        List<SysAuthGrant> grants = grantMapper.selectList(new LambdaQueryWrapper<SysAuthGrant>()
                        .eq(SysAuthGrant::getTenantId, normalizedTenantId)
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

        Map<String, RoleMenuProjectionResponse> projectionById = new LinkedHashMap<>();
        allMenus.stream()
                .filter(menu -> {
                    PermissionKey key = resolvePermissionKey(menu);
                    return key != null && grantedPermissions.contains(key) && !deniedPermissions.contains(key);
                })
                .forEach(menu -> projectionById.put(menu.getId(), directProjection(menu)));
        includeAncestorMenus(allMenus, projectionById);
        return List.copyOf(projectionById.values());
    }

    @Transactional
    public void deleteRoleAuthorizationData(String roleId) {
        deleteRoleAuthorizationData(currentTenantId(), roleId);
    }

    @Transactional
    public void deleteRoleAuthorizationData(String tenantId, String roleId) {
        String normalizedTenantId = requiredTenant(tenantId);
        String normalizedRoleId = StringHelpers.normalizeBlank(roleId);
        if (normalizedRoleId == null) {
            return;
        }
        boolean grantChanged = grantMapper.delete(new LambdaQueryWrapper<SysAuthGrant>()
                .eq(SysAuthGrant::getTenantId, normalizedTenantId)
                .eq(SysAuthGrant::getSubjectType, SUBJECT_ROLE)
                .eq(SysAuthGrant::getSubjectId, normalizedRoleId)) > 0;
        boolean fieldPolicyChanged = fieldPolicyMapper.delete(new LambdaQueryWrapper<SysAuthFieldPolicy>()
                .eq(SysAuthFieldPolicy::getTenantId, normalizedTenantId)
                .eq(SysAuthFieldPolicy::getSubjectType, SUBJECT_ROLE)
                .eq(SysAuthFieldPolicy::getSubjectId, normalizedRoleId)) > 0;
        boolean dataPolicyChanged = dataPolicyMapper.delete(new LambdaQueryWrapper<SysDataPolicy>()
                .eq(SysDataPolicy::getTenantId, normalizedTenantId)
                .eq(SysDataPolicy::getSubjectType, SUBJECT_ROLE)
                .eq(SysDataPolicy::getSubjectId, normalizedRoleId)) > 0;
        bumpVersions(false, grantChanged, fieldPolicyChanged, dataPolicyChanged);
    }

    private PermissionKey resolvePermissionKey(SysMenu menu) {
        String permissionCode = StringHelpers.normalizeBlank(menu.getPermissionCode());
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
        String resourceCode = StringHelpers.normalizeBlank(permissionCode.substring(0, separator));
        String actionCode = StringHelpers.normalizeBlank(permissionCode.substring(separator + 1));
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


    private RoleMenuProjectionResponse directProjection(SysMenu menu) {
        PermissionKey key = resolvePermissionKey(menu);
        RoleMenuProjectionResponse response = baseProjection(menu);
        response.setDerivation("DIRECT_GRANT");
        response.setPermissionCode(menu.getPermissionCode());
        if (key != null) {
            response.setResourceCode(key.resourceCode());
            response.setActionCode(key.actionCode());
        }
        return response;
    }

    private String currentTenantId() {
        return requiredTenant(com.triobase.common.core.context.SecurityContextHolder.getTenantId());
    }

    private String requiredTenant(String tenantId) {
        String normalized = StringHelpers.normalizeBlank(tenantId);
        if (normalized == null) {
            throw new com.triobase.common.core.exception.BizException(40082, "AUTHZ_TENANT_REQUIRED");
        }
        return normalized;
    }

    private RoleMenuProjectionResponse ancestorProjection(SysMenu menu, String derivedFromMenuId) {
        RoleMenuProjectionResponse response = baseProjection(menu);
        response.setDerivation("ANCESTOR");
        response.getDerivedFromMenuIds().add(derivedFromMenuId);
        return response;
    }

    private RoleMenuProjectionResponse baseProjection(SysMenu menu) {
        RoleMenuProjectionResponse response = new RoleMenuProjectionResponse();
        response.setMenuId(menu.getId());
        response.setMenuName(menu.getMenuName());
        return response;
    }

    private void includeAncestorMenus(List<SysMenu> menus,
                                      Map<String, RoleMenuProjectionResponse> projectionById) {
        if (projectionById.isEmpty()) {
            return;
        }
        Map<String, SysMenu> menuById = menus.stream()
                .collect(Collectors.toMap(SysMenu::getId, menu -> menu, (left, right) -> left));
        Set<String> selectedIds = new LinkedHashSet<>(projectionById.keySet());
        for (String selectedId : selectedIds) {
            SysMenu current = menuById.get(selectedId);
            while (current != null && StringUtils.hasText(current.getParentId())) {
                String parentId = current.getParentId();
                RoleMenuProjectionResponse existing = projectionById.get(parentId);
                if (existing == null) {
                    SysMenu parent = menuById.get(parentId);
                    if (parent == null) {
                        break;
                    }
                    projectionById.put(parentId, ancestorProjection(parent, selectedId));
                } else if ("ANCESTOR".equals(existing.getDerivation())
                        && !existing.getDerivedFromMenuIds().contains(selectedId)) {
                    existing.getDerivedFromMenuIds().add(selectedId);
                }
                current = menuById.get(parentId);
            }
        }
    }

    private record PermissionKey(String resourceCode, String actionCode) {
    }
}
