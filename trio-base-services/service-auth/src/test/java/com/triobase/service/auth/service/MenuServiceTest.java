package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.CreateMenuRequest;
import com.triobase.service.auth.dto.MenuRouteResponse;
import com.triobase.service.auth.dto.UpdateMenuRequest;
import com.triobase.service.auth.entity.SysMenu;
import com.triobase.service.auth.entity.SysPermission;
import com.triobase.service.auth.mapper.MenuMapper;
import com.triobase.service.auth.mapper.PermissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
        request.setComponent("/system/user/list");
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
        assertEquals("/system/user/list", menu.getComponent());
        assertEquals("P001", menu.getPermissionId());
        assertEquals(Short.valueOf((short) 1), menu.getVisible());
    }

    @Test
    void list_shouldReturnMenus_whenFiltersProvided() {
        SysMenu menu = new SysMenu();
        menu.setId("M001");
        menu.setMenuKey("system");
        menu.setMenuName("系统管理");
        menu.setMenuGroup("system");
        menu.setMenuType("catalog");
        menu.setStatus((short) 1);

        when(menuMapper.selectList(any())).thenReturn(List.of(menu));

        List<SysMenu> menus = menuService.list("系统", "system", "catalog", 1);

        assertEquals(1, menus.size());
        assertEquals("system", menus.get(0).getMenuGroup());
    }

    @Test
    void existsMenuKey_shouldTrimInput() {
        when(menuMapper.selectCount(any())).thenReturn(1L);

        assertTrue(menuService.existsMenuKey(" system ", null));
    }

    @Test
    void create_shouldThrow_whenLinkMissingComponent() {
        CreateMenuRequest request = new CreateMenuRequest();
        request.setMenuKey("docs");
        request.setMenuName("文档");
        request.setMenuType("link");
        request.setPath("/docs");

        BizException ex = assertThrows(BizException.class, () -> menuService.create(request));

        assertEquals(40039, ex.getCode());
    }

    @Test
    void create_shouldThrow_whenLinkComponentIsNotExternalUrl() {
        CreateMenuRequest request = new CreateMenuRequest();
        request.setMenuKey("docs");
        request.setMenuName("文档");
        request.setMenuType("link");
        request.setPath("/docs");
        request.setComponent("/system/docs/list");

        BizException ex = assertThrows(BizException.class, () -> menuService.create(request));

        assertEquals(40039, ex.getCode());
    }

    @Test
    void create_shouldThrow_whenButtonMissingPermissionCode() {
        CreateMenuRequest request = new CreateMenuRequest();
        request.setMenuKey("menusCreate");
        request.setMenuName("新增菜单");
        request.setMenuType("button");

        BizException ex = assertThrows(BizException.class, () -> menuService.create(request));

        assertEquals(40040, ex.getCode());
    }

    @Test
    void create_shouldNormalizeButtonFields() {
        CreateMenuRequest request = new CreateMenuRequest();
        request.setMenuKey("menusCreate");
        request.setMenuName("新增菜单");
        request.setMenuType("button");
        request.setPath("/should-not-persist");
        request.setComponent("/should/not/persist");
        request.setPermissionCode("system:menu:create");
        request.setHideInMenu(false);

        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);

        SysMenu menu = menuService.create(request);

        assertNull(menu.getPath());
        assertNull(menu.getComponent());
        assertEquals(Short.valueOf((short) 1), menu.getHideInMenu());
        assertEquals("system:menu:create", menu.getPermissionCode());
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

    @Test
    void listRoutes_shouldBuildVbenRouteTreeFromActiveMenus() {
        SysMenu root = new SysMenu();
        root.setId("M001");
        root.setMenuKey("system");
        root.setMenuName("系统管理");
        root.setPath("/system");
        root.setMenuType("catalog");
        root.setMenuGroup("system");
        root.setSortOrder(10);
        root.setStatus((short) 1);
        root.setIcon("ion:settings-outline");

        SysMenu child = new SysMenu();
        child.setId("M002");
        child.setParentId("M001");
        child.setMenuKey("menus");
        child.setMenuName("菜单管理");
        child.setPath("/system/menu");
        child.setComponent("/system/menu/list");
        child.setMenuType("menu");
        child.setMenuGroup("system");
        child.setSortOrder(20);
        child.setStatus((short) 1);
        child.setAffixTab((short) 1);
        child.setPermissionCode("/api/v1/menus:GET");

        SysMenu button = new SysMenu();
        button.setId("M003");
        button.setParentId("M002");
        button.setMenuKey("menusCreate");
        button.setMenuName("新增菜单");
        button.setMenuType("button");
        button.setStatus((short) 1);

        when(menuMapper.selectList(any())).thenReturn(List.of(root, child, button));

        List<MenuRouteResponse> routes = menuService.listRoutes();

        assertEquals(1, routes.size());
        assertEquals("system", routes.get(0).getName());
        assertEquals("/system/menu", routes.get(0).getRedirect());
        assertEquals("系统管理", routes.get(0).getMeta().get("title"));
        assertEquals(1, routes.get(0).getChildren().size());
        assertEquals("/system/menu/list", routes.get(0).getChildren().get(0).getComponent());
        assertEquals(Boolean.TRUE, routes.get(0).getChildren().get(0).getMeta().get("affixTab"));
        assertEquals("/api/v1/menus:GET", routes.get(0).getChildren().get(0).getAuthCode());
    }

    @Test
    void update_shouldThrow_whenParentIsDescendant() {
        UpdateMenuRequest request = new UpdateMenuRequest();
        request.setMenuKey("system");
        request.setMenuName("系统管理");
        request.setPath("/system");
        request.setMenuType("catalog");
        request.setParentId("M002");

        SysMenu current = new SysMenu();
        current.setId("M001");

        SysMenu child = new SysMenu();
        child.setId("M002");
        child.setParentId("M001");

        when(menuMapper.selectById("M001")).thenReturn(current);
        when(menuMapper.selectById("M002")).thenReturn(child);

        BizException ex = assertThrows(BizException.class, () -> menuService.update("M001", request));

        assertEquals(40036, ex.getCode());
    }

    @Test
    void update_shouldThrow_whenParentIsButton() {
        UpdateMenuRequest request = new UpdateMenuRequest();
        request.setMenuKey("create");
        request.setMenuName("新增菜单");
        request.setPath("/system/menu/create");
        request.setComponent("/system/menu/create");
        request.setMenuType("menu");
        request.setParentId("M002");

        SysMenu current = new SysMenu();
        current.setId("M001");

        SysMenu parent = new SysMenu();
        parent.setId("M002");
        parent.setMenuType("button");

        when(menuMapper.selectById("M001")).thenReturn(current);
        when(menuMapper.selectById("M002")).thenReturn(parent);

        BizException ex = assertThrows(BizException.class, () -> menuService.update("M001", request));

        assertEquals(40037, ex.getCode());
    }
}
