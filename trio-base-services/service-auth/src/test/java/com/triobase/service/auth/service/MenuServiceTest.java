package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.CreateMenuRequest;
import com.triobase.service.auth.dto.MenuRouteResponse;
import com.triobase.service.auth.dto.UpdateMenuRequest;
import com.triobase.service.auth.entity.SysMenu;
import com.triobase.service.auth.entity.SysAuthAction;
import com.triobase.service.auth.entity.SysAuthResource;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import com.triobase.service.auth.mapper.MenuMapper;
import com.triobase.service.auth.mapper.UserMapper;
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
    private AuthResourceMapper authResourceMapper;

    @Mock
    private AuthActionMapper authActionMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PageCapabilityCatalogFacade pageCapabilityCatalogFacade;

    @InjectMocks
    private MenuService menuService;

    @Test
    void create_shouldPersistMenu_whenRequestValid() {
        CreateMenuRequest request = new CreateMenuRequest();
        request.setMenuKey("users");
        request.setMenuName("用户管理");
        request.setPageCode("SYSTEM.USER");
        request.setPath("/admin/users");
        request.setComponent("/system/user/list");
        request.setPermissionCode("/api/v1/users:GET");

        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(pageCapabilityCatalogFacade.pageExists(null, "SYSTEM.USER")).thenReturn(true);
        when(authResourceMapper.selectCount(any())).thenReturn(1L);
        when(authActionMapper.selectCount(any())).thenReturn(1L);
        when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);

        SysMenu menu = menuService.create(request);

        assertNotNull(menu.getId());
        assertEquals("users", menu.getMenuKey());
        assertEquals("SYSTEM.USER", menu.getPageCode());
        assertEquals("/admin/users", menu.getPath());
        assertEquals("/system/user/list", menu.getComponent());
        assertEquals("/api/v1/users:GET", menu.getPermissionCode());
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
        assertEquals("MISSING", menus.get(0).getAuthorizationMappingStatus());
    }

    @Test
    void update_shouldRoundTripPageCode() {
        SysMenu existing = new SysMenu();
        existing.setId("M001");
        existing.setMenuType("menu");

        UpdateMenuRequest request = new UpdateMenuRequest();
        request.setMenuKey("menus");
        request.setMenuName("菜单管理");
        request.setPageCode(" SYSTEM.MENU ");
        request.setPath("/system/menu");
        request.setComponent("/system/menu/list");
        request.setMenuType("menu");

        when(menuMapper.selectById("M001")).thenReturn(existing);
        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(pageCapabilityCatalogFacade.pageExists(null, "SYSTEM.MENU")).thenReturn(true);

        SysMenu updated = menuService.update("M001", request);

        assertEquals("SYSTEM.MENU", updated.getPageCode());
    }

    @Test
    void list_shouldKeepLegacyMenuWithoutPageCodeReadable() {
        SysMenu legacy = new SysMenu();
        legacy.setId("M_LEGACY");
        legacy.setMenuKey("legacyPage");
        legacy.setMenuName("历史页面");
        legacy.setMenuType("menu");
        when(menuMapper.selectList(any())).thenReturn(List.of(legacy));

        SysMenu result = menuService.list().getFirst();

        assertNull(result.getPageCode());
        assertEquals("历史页面", result.getMenuName());
    }

    @Test
    void create_shouldDiscardPageCodeForCatalogNodes() {
        CreateMenuRequest request = new CreateMenuRequest();
        request.setMenuKey("system");
        request.setMenuName("系统管理");
        request.setPageCode("SYSTEM.MENU");
        request.setPath("/system");
        request.setMenuType("catalog");
        when(menuMapper.selectCount(any())).thenReturn(0L);

        SysMenu menu = menuService.create(request);

        assertNull(menu.getPageCode());
    }

    @Test
    void list_shouldExposeValidNormalizedAuthorizationMapping() {
        SysMenu menu = new SysMenu();
        menu.setId("M_USERS");
        menu.setPermissionCode(" /api/v1/users:GET ");
        SysAuthResource resource = new SysAuthResource();
        resource.setResourceCode("/api/v1/users");
        resource.setLifecycleStatus("ACTIVE");
        SysAuthAction action = new SysAuthAction();
        action.setResourceCode("/api/v1/users");
        action.setActionCode("GET");
        action.setStatus((short) 1);
        when(menuMapper.selectList(any())).thenReturn(List.of(menu));
        when(authResourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(authActionMapper.selectList(any())).thenReturn(List.of(action));

        SysMenu result = menuService.list().getFirst();

        assertEquals("/api/v1/users", result.getAuthorizationResourceCode());
        assertEquals("GET", result.getAuthorizationActionCode());
        assertEquals("VALID", result.getAuthorizationMappingStatus());
    }

    @Test
    void list_shouldClassifyInvalidAndUnregisteredMappings() {
        SysMenu malformed = new SysMenu();
        malformed.setId("M_BAD");
        malformed.setPermissionCode("not-a-permission");
        SysMenu unregistered = new SysMenu();
        unregistered.setId("M_UNKNOWN");
        unregistered.setPermissionCode("UNKNOWN:VIEW");
        when(menuMapper.selectList(any())).thenReturn(List.of(malformed, unregistered));
        when(authResourceMapper.selectList(any())).thenReturn(List.of());
        when(authActionMapper.selectList(any())).thenReturn(List.of());

        List<SysMenu> result = menuService.list();

        assertEquals("INVALID_FORMAT", result.get(0).getAuthorizationMappingStatus());
        assertEquals("RESOURCE_NOT_FOUND", result.get(1).getAuthorizationMappingStatus());
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
    void create_shouldAllowLinkWithoutRoutePath() {
        CreateMenuRequest request = new CreateMenuRequest();
        request.setMenuKey("docs");
        request.setMenuName("Docs");
        request.setMenuType("link");
        request.setComponent("https://example.com/docs");

        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);

        SysMenu menu = menuService.create(request);

        assertNull(menu.getPath());
        assertEquals("https://example.com/docs", menu.getComponent());
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
        request.setPermissionCode("/api/v1/menus:POST");
        request.setHideInMenu(false);

        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(authResourceMapper.selectCount(any())).thenReturn(1L);
        when(authActionMapper.selectCount(any())).thenReturn(1L);
        when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);

        SysMenu menu = menuService.create(request);

        assertNull(menu.getPath());
        assertNull(menu.getComponent());
        assertEquals(Short.valueOf((short) 1), menu.getHideInMenu());
        assertEquals("/api/v1/menus:POST", menu.getPermissionCode());
    }

    @Test
    void create_shouldThrow_whenPermissionCodeIsNotRegistered() {
        CreateMenuRequest request = new CreateMenuRequest();
        request.setMenuKey("expenseCreate");
        request.setMenuName("发起费用");
        request.setMenuType("button");
        request.setPermissionCode("FORM:EXPENSE:CREATE");

        when(authResourceMapper.selectCount(any())).thenReturn(1L);
        when(authActionMapper.selectCount(any())).thenReturn(0L);

        BizException ex = assertThrows(BizException.class, () -> menuService.create(request));

        assertEquals(40432, ex.getCode());
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
    void listRoutesForUser_shouldDeriveRoutesFromGrantPermissionsAndHonorDeny() {
        SysMenu root = new SysMenu();
        root.setId("M001");
        root.setMenuKey("system");
        root.setMenuName("系统管理");
        root.setPath("/system");
        root.setMenuType("catalog");
        root.setMenuGroup("system");
        root.setStatus((short) 1);

        SysMenu menuAllowed = new SysMenu();
        menuAllowed.setId("M002");
        menuAllowed.setParentId("M001");
        menuAllowed.setMenuKey("menus");
        menuAllowed.setMenuName("菜单管理");
        menuAllowed.setPath("/system/menu");
        menuAllowed.setComponent("/system/menu/list");
        menuAllowed.setMenuType("menu");
        menuAllowed.setStatus((short) 1);
        menuAllowed.setPermissionCode("/api/v1/menus:GET");

        SysMenu menuDenied = new SysMenu();
        menuDenied.setId("M003");
        menuDenied.setParentId("M001");
        menuDenied.setMenuKey("roles");
        menuDenied.setMenuName("角色管理");
        menuDenied.setPath("/system/role");
        menuDenied.setComponent("/system/role/list");
        menuDenied.setMenuType("menu");
        menuDenied.setStatus((short) 1);
        menuDenied.setPermissionCode("/api/v1/roles:GET");

        SysMenu menuWithoutGrant = new SysMenu();
        menuWithoutGrant.setId("M004");
        menuWithoutGrant.setParentId("M001");
        menuWithoutGrant.setMenuKey("publicReport");
        menuWithoutGrant.setMenuName("未授权报表");
        menuWithoutGrant.setPath("/system/public-report");
        menuWithoutGrant.setComponent("/system/public-report/list");
        menuWithoutGrant.setMenuType("menu");
        menuWithoutGrant.setStatus((short) 1);

        when(menuMapper.selectList(any())).thenReturn(List.of(root, menuAllowed, menuDenied, menuWithoutGrant));
        when(userMapper.selectPermissionsByUserId("U001"))
                .thenReturn(List.of("/api/v1/menus:GET", "/api/v1/roles:GET"));
        when(userMapper.selectDeniedPermissionsByUserId("U001"))
                .thenReturn(List.of("/api/v1/roles:GET"));

        List<MenuRouteResponse> routes = menuService.listRoutesForUser("U001");

        assertEquals(1, routes.size());
        assertEquals("system", routes.get(0).getName());
        assertEquals(1, routes.get(0).getChildren().size());
        assertEquals("menus", routes.get(0).getChildren().get(0).getName());
    }

    @Test
    void listRoutes_shouldBuildSyntheticPathForLinkWithoutRoutePath() {
        SysMenu link = new SysMenu();
        link.setId("M001");
        link.setMenuKey("docs");
        link.setMenuName("Docs");
        link.setMenuType("link");
        link.setComponent("https://example.com/docs");
        link.setStatus((short) 1);

        when(menuMapper.selectList(any())).thenReturn(List.of(link));

        List<MenuRouteResponse> routes = menuService.listRoutes();

        assertEquals(1, routes.size());
        assertEquals("/external/docs", routes.get(0).getPath());
        assertEquals("IFrameView", routes.get(0).getComponent());
        assertEquals("https://example.com/docs", routes.get(0).getMeta().get("link"));
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
