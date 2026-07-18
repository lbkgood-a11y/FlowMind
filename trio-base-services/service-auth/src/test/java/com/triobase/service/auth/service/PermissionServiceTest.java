package com.triobase.service.auth.service;

import com.triobase.service.auth.entity.SysPermission;
import com.triobase.service.auth.mapper.MenuMapper;
import com.triobase.service.auth.mapper.PermissionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionServiceTest {

    private final PermissionMapper permissionMapper = mock(PermissionMapper.class);
    private final PermissionService service = new PermissionService(permissionMapper, mock(MenuMapper.class));

    @Test
    void missingRegisteredCodesReturnsOnlyUnknownPermissions() {
        when(permissionMapper.selectList(null)).thenReturn(List.of(
                permission("/api/v1/forms", "GET"),
                permission("/api/v1/forms/*/submit", "POST")));

        List<String> missing = service.missingRegisteredCodes(List.of(
                "/api/v1/forms:GET",
                "/api/v1/forms/*/submit:POST",
                "/api/v1/unknown:GET"));

        assertThat(missing).containsExactly("/api/v1/unknown:GET");
    }

    private SysPermission permission(String resource, String action) {
        SysPermission permission = new SysPermission();
        permission.setResource(resource);
        permission.setAction(action);
        return permission;
    }
}
