package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.CreateRoleRequest;
import com.triobase.service.auth.dto.UpdateRoleRequest;
import com.triobase.service.auth.entity.SysPermission;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysRolePermission;
import com.triobase.service.auth.mapper.PermissionMapper;
import com.triobase.service.auth.mapper.RoleMapper;
import com.triobase.service.auth.mapper.RolePermissionMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    @Mock
    private PermissionMapper permissionMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @InjectMocks
    private RoleService roleService;

    @Test
    void create_shouldPersistRoleAndPermissions_whenRequestValid() {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setRoleCode("ADMIN");
        request.setRoleName("超级管理员");
        request.setStatus(1);
        request.setPermissionIds(List.of("P001", "P002", "P001", " "));

        SysPermission permission = new SysPermission();
        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(permissionMapper.selectById("P001")).thenReturn(permission);
        when(permissionMapper.selectById("P002")).thenReturn(permission);
        when(roleMapper.insert(any(SysRole.class))).thenReturn(1);

        SysRole role = roleService.create(request);

        assertNotNull(role.getId());
        assertEquals("ADMIN", role.getRoleCode());
        assertEquals("超级管理员", role.getRoleName());
        assertEquals(Short.valueOf((short) 1), role.getStatus());
        verify(rolePermissionMapper, times(2)).insert(any(SysRolePermission.class));
    }

    @Test
    void existsRoleCode_shouldTrimInput() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        assertTrue(roleService.existsRoleCode(" ADMIN ", null));
    }

    @Test
    void list_shouldReturnRoles_whenFiltersProvided() {
        SysRole role = new SysRole();
        role.setId("R001");
        role.setRoleCode("ADMIN");
        role.setRoleName("超级管理员");
        role.setStatus((short) 1);

        when(roleMapper.selectList(any())).thenReturn(List.of(role));

        List<SysRole> roles = roleService.list("admin", 1);

        assertEquals(1, roles.size());
        assertEquals("ADMIN", roles.get(0).getRoleCode());
    }

    @Test
    void update_shouldThrow_whenPermissionMissing() {
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setRoleName("租户管理员");
        request.setPermissionIds(List.of("P404"));

        SysRole role = new SysRole();
        role.setId("R001");
        role.setRoleCode("TENANT_ADMIN");

        when(roleMapper.selectById("R001")).thenReturn(role);
        when(permissionMapper.selectById("P404")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> roleService.update("R001", request));

        assertEquals(40421, ex.getCode());
        verify(rolePermissionMapper, never()).delete(any());
    }

    @Test
    void updateStatus_shouldPersistStatus() {
        SysRole role = new SysRole();
        role.setId("R001");
        role.setStatus((short) 1);

        when(roleMapper.selectById("R001")).thenReturn(role);

        SysRole updated = roleService.updateStatus("R001", 0);

        assertEquals(Short.valueOf((short) 0), updated.getStatus());
        verify(roleMapper).updateById(role);
    }

    @Test
    void delete_shouldThrow_whenRoleAssignedUsers() {
        SysRole role = new SysRole();
        role.setId("R001");

        when(roleMapper.selectById("R001")).thenReturn(role);
        when(userRoleMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> roleService.delete("R001"));

        assertEquals(40043, ex.getCode());
        verify(roleMapper, never()).deleteById("R001");
    }
}
