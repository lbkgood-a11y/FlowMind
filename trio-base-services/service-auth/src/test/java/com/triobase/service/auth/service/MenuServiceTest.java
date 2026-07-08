package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.CreateMenuRequest;
import com.triobase.service.auth.entity.SysMenu;
import com.triobase.service.auth.entity.SysPermission;
import com.triobase.service.auth.mapper.MenuMapper;
import com.triobase.service.auth.mapper.PermissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuMapper menuMapper;

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private MenuService menuService;

    @Test
    void create_shouldPersistMenu_whenRequestValid() {
        CreateMenuRequest request = new CreateMenuRequest();
        request.setMenuKey("users");
        request.setMenuName("用户管理");
        request.setPath("/admin/users");
        request.setPermissionId("P001");

        SysPermission permission = new SysPermission();
        permission.setId("P001");

        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(permissionMapper.selectById("P001")).thenReturn(permission);
        when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);

        SysMenu menu = menuService.create(request);

        assertNotNull(menu.getId());
        assertEquals("users", menu.getMenuKey());
        assertEquals("/admin/users", menu.getPath());
        assertEquals("P001", menu.getPermissionId());
        assertEquals(Short.valueOf((short) 1), menu.getVisible());
    }

    @Test
    void delete_shouldThrow_whenMenuHasChildren() {
        SysMenu parent = new SysMenu();
        parent.setId("M001");

        when(menuMapper.selectById("M001")).thenReturn(parent);
        when(menuMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> menuService.delete("M001"));

        assertEquals(40034, ex.getCode());
    }
}
