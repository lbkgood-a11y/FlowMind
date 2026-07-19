package com.triobase.service.auth.service;

import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysMenu;
import com.triobase.service.auth.mapper.AuthFieldPolicyMapper;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.DataPolicyMapper;
import com.triobase.service.auth.mapper.MenuMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAuthorizationDataServiceTest {

    @Mock
    private MenuMapper menuMapper;

    @Mock
    private AuthGrantMapper grantMapper;

    @Mock
    private AuthFieldPolicyMapper fieldPolicyMapper;

    @Mock
    private DataPolicyMapper dataPolicyMapper;

    @Mock
    private AuthorizationVersionService versionService;

    @InjectMocks
    private RoleAuthorizationDataService dataService;

    @Test
    void menuIdsForRole_shouldDeriveSelectedMenusFromEffectiveGrantsAndAncestors() {
        SysMenu root = menu("ROOT", null, "系统管理");
        root.setMenuType("catalog");
        SysMenu userMenu = menu("M001", "/api/v1/users:GET", "用户列表");
        userMenu.setParentId("ROOT");
        SysMenu roleMenu = menu("M002", "/api/v1/roles:GET", "角色列表");
        roleMenu.setParentId("ROOT");
        SysAuthGrant userAllow = grant("G_USER_ALLOW", "/api/v1/users", "GET", "用户列表");
        SysAuthGrant roleAllow = grant("G_ROLE_ALLOW", "/api/v1/roles", "GET", "角色列表");
        SysAuthGrant roleDeny = grant("G_ROLE_DENY", "/api/v1/roles", "GET", "拒绝角色列表");
        roleDeny.setEffect("DENY");

        when(menuMapper.selectList(any())).thenReturn(List.of(root, userMenu, roleMenu));
        when(grantMapper.selectList(any())).thenReturn(List.of(userAllow, roleAllow, roleDeny));

        List<String> menuIds = dataService.menuIdsForRole("R001");

        assertTrue(menuIds.contains("ROOT"));
        assertTrue(menuIds.contains("M001"));
        assertEquals(2, menuIds.size());
    }

    @Test
    void deleteRoleAuthorizationData_shouldRemoveRoleScopedAuthorizationData() {
        when(grantMapper.delete(any())).thenReturn(1);
        when(fieldPolicyMapper.delete(any())).thenReturn(1);
        when(dataPolicyMapper.delete(any())).thenReturn(1);

        dataService.deleteRoleAuthorizationData("R001");

        verify(grantMapper).delete(any());
        verify(fieldPolicyMapper).delete(any());
        verify(dataPolicyMapper).delete(any());
        verify(versionService).bump(AuthorizationVersionService.GRANT);
        verify(versionService).bump(AuthorizationVersionService.FIELD_POLICY);
        verify(versionService).bump(AuthorizationVersionService.DATA_POLICY);
        verify(versionService).bump(AuthorizationVersionService.AUTHORIZATION);
    }

    private SysMenu menu(String id, String permissionCode, String name) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setMenuName(name);
        menu.setDescription(name);
        menu.setPermissionCode(permissionCode);
        return menu;
    }

    private SysAuthGrant grant(String id, String resourceCode, String actionCode, String description) {
        SysAuthGrant grant = new SysAuthGrant();
        grant.setId(id);
        grant.setTenantId("default");
        grant.setSubjectType("ROLE");
        grant.setSubjectId("R001");
        grant.setResourceCode(resourceCode);
        grant.setActionCode(actionCode);
        grant.setEffect("ALLOW");
        grant.setStatus((short) 1);
        grant.setDescription(description);
        return grant;
    }
}
