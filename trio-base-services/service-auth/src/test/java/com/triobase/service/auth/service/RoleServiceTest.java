package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.CreateRoleRequest;
import com.triobase.service.auth.dto.UpdateRoleRequest;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.mapper.RoleMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private RoleAuthorizationDataService roleAuthorizationDataService;

    @InjectMocks
    private RoleService roleService;

    @Test
    void create_shouldPersistRoleOnly_whenRequestValid() {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setRoleCode("ADMIN");
        request.setRoleName("Administrator");
        request.setStatus(1);

        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.insert(any(SysRole.class))).thenReturn(1);

        SysRole role = roleService.create(request);

        assertNotNull(role.getId());
        assertEquals("ADMIN", role.getRoleCode());
        assertEquals("Administrator", role.getRoleName());
        assertEquals(Short.valueOf((short) 1), role.getStatus());
        verifyNoInteractions(roleAuthorizationDataService);
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
        role.setRoleName("Administrator");
        role.setStatus((short) 1);

        when(roleMapper.selectList(any())).thenReturn(List.of(role));

        List<SysRole> roles = roleService.list("admin", 1);

        assertEquals(1, roles.size());
        assertEquals("ADMIN", roles.get(0).getRoleCode());
    }

    @Test
    void update_shouldPreserveAuthorization_whenUpdatingRoleDetails() {
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setRoleName("Tenant Administrator");
        request.setStatus(1);

        SysRole role = new SysRole();
        role.setId("R001");
        role.setRoleCode("TENANT_ADMIN");

        when(roleMapper.selectById("R001")).thenReturn(role);
        when(roleMapper.updateById(role)).thenReturn(1);

        SysRole updated = roleService.update("R001", request);

        assertEquals("Tenant Administrator", updated.getRoleName());
        verifyNoInteractions(roleAuthorizationDataService);
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
        verify(roleAuthorizationDataService, never()).deleteRoleAuthorizationData("R001");
    }

    @Test
    void delete_shouldCleanAuthorizationData_whenRoleHasNoUsers() {
        SysRole role = new SysRole();
        role.setId("R099");

        when(roleMapper.selectById("R099")).thenReturn(role);
        when(userRoleMapper.selectCount(any())).thenReturn(0L);

        roleService.delete("R099");

        verify(roleAuthorizationDataService).deleteRoleAuthorizationData("R099");
        verify(roleMapper).deleteById("R099");
    }
}
