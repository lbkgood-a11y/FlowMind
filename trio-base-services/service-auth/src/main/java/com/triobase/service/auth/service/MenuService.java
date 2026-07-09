package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.CreateMenuRequest;
import com.triobase.service.auth.dto.MenuRouteResponse;
import com.triobase.service.auth.dto.UpdateMenuRequest;
import com.triobase.service.auth.entity.SysMenu;
import com.triobase.service.auth.entity.SysPermission;
import com.triobase.service.auth.mapper.MenuMapper;
import com.triobase.service.auth.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private static final String DEFAULT_GROUP = "general";
    private static final String TYPE_BUTTON = "button";
    private static final String TYPE_CATALOG = "catalog";
    private static final String TYPE_EMBEDDED = "embedded";
    private static final String TYPE_LINK = "link";
    private static final String TYPE_MENU = "menu";
    private static final Set<String> MENU_TYPES = Set.of(TYPE_BUTTON, TYPE_CATALOG, TYPE_EMBEDDED, TYPE_LINK, TYPE_MENU);
    private static final Set<String> ROUTE_COMPONENT_TYPES = Set.of(TYPE_EMBEDDED, TYPE_LINK);

    private final MenuMapper menuMapper;
    private final PermissionMapper permissionMapper;

    public List<SysMenu> list() {
        return list(null, null, null, null);
    }

    public List<SysMenu> list(String keyword, String menuGroup, String menuType, Integer status) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>();
        String normalizedKeyword = normalizeBlank(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(query -> query
                    .like(SysMenu::getMenuKey, normalizedKeyword)
                    .or()
                    .like(SysMenu::getMenuName, normalizedKeyword)
                    .or()
                    .like(SysMenu::getPath, normalizedKeyword)
                    .or()
                    .like(SysMenu::getComponent, normalizedKeyword)
                    .or()
                    .like(SysMenu::getPermissionCode, normalizedKeyword));
        }
        String normalizedGroup = normalizeBlank(menuGroup);
        if (normalizedGroup != null) {
            wrapper.eq(SysMenu::getMenuGroup, normalizedGroup);
        }
        String normalizedType = normalizeBlank(menuType);
        if (normalizedType != null) {
            wrapper.eq(SysMenu::getMenuType, normalizedType);
        }
        if (status != null) {
            wrapper.eq(SysMenu::getStatus, toStatus(status));
        }

        return menuMapper.selectList(wrapper
                .orderByAsc(SysMenu::getMenuGroup)
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getCreatedAt));
    }

    public List<MenuRouteResponse> listRoutes() {
        List<SysMenu> routeMenus = list().stream()
                .filter(menu -> !TYPE_BUTTON.equals(normalizeMenuType(menu.getMenuType())))
                .filter(this::isActive)
                .collect(Collectors.toList());
        return buildRouteTree(routeMenus);
    }

    public boolean existsMenuKey(String menuKey, String excludeId) {
        String normalizedKey = normalizeBlank(menuKey);
        if (normalizedKey == null) {
            return false;
        }
        return countMenuKey(normalizedKey, excludeId) > 0;
    }

    public boolean existsPath(String path, String excludeId) {
        String normalizedPath = normalizeBlank(path);
        if (normalizedPath == null) {
            return false;
        }
        return countPath(normalizedPath, excludeId) > 0;
    }

    @Transactional
    public SysMenu create(CreateMenuRequest request) {
        String menuType = normalizeMenuType(request.getMenuType());
        validateRequired(request.getMenuKey(), request.getMenuName(), request.getPath(),
                request.getComponent(), request.getPermissionCode(), menuType);
        validateParent(request.getParentId(), null);
        validatePermission(request.getPermissionId());
        validateUniqueMenuKey(request.getMenuKey(), null);
        validateUniquePath(normalizePathForType(request.getPath(), menuType), null);

        SysMenu menu = new SysMenu();
        menu.setId("M" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        applyCreateRequest(menu, request);
        menuMapper.insert(menu);
        return menu;
    }

    @Transactional
    public SysMenu update(String id, UpdateMenuRequest request) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException(40433, "MENU_NOT_FOUND");
        }

        String menuType = normalizeMenuType(request.getMenuType());
        validateRequired(request.getMenuKey(), request.getMenuName(), request.getPath(),
                request.getComponent(), request.getPermissionCode(), menuType);
        validateParent(request.getParentId(), id);
        validatePermission(request.getPermissionId());
        validateUniqueMenuKey(request.getMenuKey(), id);
        validateUniquePath(normalizePathForType(request.getPath(), menuType), id);

        applyUpdateRequest(menu, request);
        menuMapper.updateById(menu);
        return menu;
    }

    @Transactional
    public SysMenu updateStatus(String id, Integer status) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException(40433, "MENU_NOT_FOUND");
        }
        menu.setStatus(toStatus(status));
        menu.setVisible(menu.getStatus());
        menuMapper.updateById(menu);
        return menu;
    }

    @Transactional
    public void delete(String id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException(40433, "MENU_NOT_FOUND");
        }

        if (menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, id)) > 0) {
            throw new BizException(40034, "MENU_HAS_CHILDREN");
        }

        menuMapper.deleteById(id);
    }

    private void applyCreateRequest(SysMenu menu, CreateMenuRequest request) {
        String menuType = normalizeMenuType(request.getMenuType());
        menu.setParentId(normalizeBlank(request.getParentId()));
        menu.setMenuKey(request.getMenuKey().trim());
        menu.setMenuName(request.getMenuName().trim());
        menu.setPath(normalizePathForType(request.getPath(), menuType));
        menu.setComponent(normalizeComponentForType(request.getComponent(), menuType));
        menu.setIcon(normalizeBlank(request.getIcon()));
        menu.setActiveIcon(normalizeBlank(request.getActiveIcon()));
        menu.setActivePath(normalizeBlank(request.getActivePath()));
        menu.setMenuType(menuType);
        menu.setMenuGroup(StringUtils.hasText(request.getMenuGroup()) ? request.getMenuGroup().trim() : DEFAULT_GROUP);
        menu.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        menu.setStatus(toStatus(request.getStatus()));
        menu.setVisible(request.getVisible() == null ? menu.getStatus() : toShort(request.getVisible()));
        menu.setKeepAlive(toShort(request.getKeepAlive()));
        menu.setAffixTab(toShort(request.getAffixTab()));
        menu.setHideInMenu(TYPE_BUTTON.equals(menuType) ? (short) 1 : toShort(request.getHideInMenu()));
        menu.setHideChildrenInMenu(toShort(request.getHideChildrenInMenu()));
        menu.setHideInBreadcrumb(toShort(request.getHideInBreadcrumb()));
        menu.setHideInTab(toShort(request.getHideInTab()));
        menu.setBadge(normalizeBlank(request.getBadge()));
        menu.setBadgeType(normalizeBlank(request.getBadgeType()));
        menu.setBadgeVariant(normalizeBlank(request.getBadgeVariant()));
        menu.setPermissionId(normalizeBlank(request.getPermissionId()));
        menu.setPermissionCode(normalizeBlank(request.getPermissionCode()));
        menu.setDescription(normalizeBlank(request.getDescription()));
    }

    private void applyUpdateRequest(SysMenu menu, UpdateMenuRequest request) {
        String menuType = normalizeMenuType(request.getMenuType());
        menu.setParentId(normalizeBlank(request.getParentId()));
        menu.setMenuKey(request.getMenuKey().trim());
        menu.setMenuName(request.getMenuName().trim());
        menu.setPath(normalizePathForType(request.getPath(), menuType));
        menu.setComponent(normalizeComponentForType(request.getComponent(), menuType));
        menu.setIcon(normalizeBlank(request.getIcon()));
        menu.setActiveIcon(normalizeBlank(request.getActiveIcon()));
        menu.setActivePath(normalizeBlank(request.getActivePath()));
        menu.setMenuType(menuType);
        menu.setMenuGroup(StringUtils.hasText(request.getMenuGroup()) ? request.getMenuGroup().trim() : DEFAULT_GROUP);
        menu.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        menu.setStatus(toStatus(request.getStatus()));
        menu.setVisible(request.getVisible() == null ? menu.getStatus() : toShort(request.getVisible()));
        menu.setKeepAlive(toShort(request.getKeepAlive()));
        menu.setAffixTab(toShort(request.getAffixTab()));
        menu.setHideInMenu(TYPE_BUTTON.equals(menuType) ? (short) 1 : toShort(request.getHideInMenu()));
        menu.setHideChildrenInMenu(toShort(request.getHideChildrenInMenu()));
        menu.setHideInBreadcrumb(toShort(request.getHideInBreadcrumb()));
        menu.setHideInTab(toShort(request.getHideInTab()));
        menu.setBadge(normalizeBlank(request.getBadge()));
        menu.setBadgeType(normalizeBlank(request.getBadgeType()));
        menu.setBadgeVariant(normalizeBlank(request.getBadgeVariant()));
        menu.setPermissionId(normalizeBlank(request.getPermissionId()));
        menu.setPermissionCode(normalizeBlank(request.getPermissionCode()));
        menu.setDescription(normalizeBlank(request.getDescription()));
    }

    private void validateRequired(String menuKey, String menuName, String path,
                                  String component, String permissionCode, String menuType) {
        if (!StringUtils.hasText(menuKey) || !StringUtils.hasText(menuName)) {
            throw new BizException(40031, "MENU_KEY_NAME_REQUIRED");
        }
        String normalizedType = normalizeMenuType(menuType);
        if (!MENU_TYPES.contains(normalizedType)) {
            throw new BizException(40038, "MENU_TYPE_INVALID");
        }
        if (!TYPE_BUTTON.equals(normalizedType) && !StringUtils.hasText(path)) {
            throw new BizException(40035, "MENU_PATH_REQUIRED");
        }
        if (TYPE_MENU.equals(normalizedType) && !StringUtils.hasText(component)) {
            throw new BizException(40039, "MENU_COMPONENT_REQUIRED");
        }
        if ((TYPE_EMBEDDED.equals(normalizedType) || TYPE_LINK.equals(normalizedType))
                && !isExternalUrl(component)) {
            throw new BizException(40039, "MENU_COMPONENT_REQUIRED");
        }
        if (TYPE_BUTTON.equals(normalizedType) && !StringUtils.hasText(permissionCode)) {
            throw new BizException(40040, "MENU_PERMISSION_CODE_REQUIRED");
        }
    }

    private void validateParent(String parentId, String currentId) {
        if (!StringUtils.hasText(parentId)) {
            return;
        }

        String ancestorId = parentId;
        Set<String> visitedIds = new HashSet<>();
        while (StringUtils.hasText(ancestorId)) {
            if (ancestorId.equals(currentId) || !visitedIds.add(ancestorId)) {
                throw new BizException(40036, "MENU_PARENT_CANNOT_BE_SELF");
            }
            SysMenu ancestor = menuMapper.selectById(ancestorId);
            if (ancestor == null) {
                throw new BizException(40431, "PARENT_MENU_NOT_FOUND");
            }
            if (ancestorId.equals(parentId) && TYPE_BUTTON.equals(normalizeMenuType(ancestor.getMenuType()))) {
                throw new BizException(40037, "MENU_PARENT_CANNOT_BE_BUTTON");
            }
            ancestorId = ancestor.getParentId();
        }
    }

    private void validatePermission(String permissionId) {
        if (!StringUtils.hasText(permissionId)) {
            return;
        }
        SysPermission permission = permissionMapper.selectById(permissionId);
        if (permission == null) {
            throw new BizException(40432, "PERMISSION_NOT_FOUND");
        }
    }

    private void validateUniqueMenuKey(String menuKey, String currentId) {
        String normalizedKey = normalizeBlank(menuKey);
        if (normalizedKey != null && countMenuKey(normalizedKey, currentId) > 0) {
            throw new BizException(40032, "MENU_KEY_ALREADY_EXISTS");
        }
    }

    private void validateUniquePath(String path, String currentId) {
        String normalizedPath = normalizeBlank(path);
        if (normalizedPath != null && countPath(normalizedPath, currentId) > 0) {
            throw new BizException(40033, "MENU_PATH_ALREADY_EXISTS");
        }
    }

    private long countMenuKey(String menuKey, String currentId) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getMenuKey, menuKey);
        if (StringUtils.hasText(currentId)) {
            wrapper.ne(SysMenu::getId, currentId);
        }
        return menuMapper.selectCount(wrapper);
    }

    private long countPath(String path, String currentId) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPath, path);
        if (StringUtils.hasText(currentId)) {
            wrapper.ne(SysMenu::getId, currentId);
        }
        return menuMapper.selectCount(wrapper);
    }

    private Short toShort(Boolean value) {
        return Boolean.TRUE.equals(value) ? (short) 1 : (short) 0;
    }

    private Short toStatus(Integer value) {
        return value != null && value == 0 ? (short) 0 : (short) 1;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<MenuRouteResponse> buildRouteTree(List<SysMenu> menus) {
        Set<String> menuIds = menus.stream()
                .map(SysMenu::getId)
                .collect(Collectors.toSet());
        Map<String, List<SysMenu>> childrenByParent = menus.stream()
                .filter(menu -> StringUtils.hasText(menu.getParentId()) && menuIds.contains(menu.getParentId()))
                .collect(Collectors.groupingBy(SysMenu::getParentId, LinkedHashMap::new, Collectors.toList()));

        return sortMenus(menus).stream()
                .filter(menu -> !StringUtils.hasText(menu.getParentId()) || !menuIds.contains(menu.getParentId()))
                .map(menu -> toRoute(menu, childrenByParent))
                .collect(Collectors.toList());
    }

    private MenuRouteResponse toRoute(SysMenu menu, Map<String, List<SysMenu>> childrenByParent) {
        MenuRouteResponse route = new MenuRouteResponse();
        String menuType = normalizeMenuType(menu.getMenuType());
        route.setName(normalizeRouteName(menu));
        route.setPath(normalizeBlank(menu.getPath()));
        route.setType(menuType);
        route.setAuthCode(normalizeBlank(menu.getPermissionCode()));
        route.setComponent(resolveRouteComponent(menu, menuType));
        route.setMeta(buildRouteMeta(menu, menuType));

        List<MenuRouteResponse> children = sortMenus(childrenByParent.getOrDefault(menu.getId(), List.of()))
                .stream()
                .map(child -> toRoute(child, childrenByParent))
                .collect(Collectors.toCollection(ArrayList::new));
        route.setChildren(children);
        if (!children.isEmpty()) {
            route.setRedirect(resolveRedirect(route.getPath(), children.get(0).getPath()));
        }
        return route;
    }

    private Map<String, Object> buildRouteMeta(SysMenu menu, String menuType) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", StringUtils.hasText(menu.getMenuName()) ? menu.getMenuName().trim() : normalizeRouteName(menu));
        putText(meta, "icon", menu.getIcon());
        putText(meta, "activeIcon", menu.getActiveIcon());
        putText(meta, "activePath", menu.getActivePath());
        putText(meta, "badge", menu.getBadge());
        putText(meta, "badgeType", menu.getBadgeType());
        putText(meta, "badgeVariants", menu.getBadgeVariant());
        if (menu.getSortOrder() != null) {
            meta.put("order", menu.getSortOrder());
        }
        putTrue(meta, "keepAlive", menu.getKeepAlive());
        putTrue(meta, "affixTab", menu.getAffixTab());
        putTrue(meta, "hideInMenu", menu.getHideInMenu());
        putTrue(meta, "hideChildrenInMenu", menu.getHideChildrenInMenu());
        putTrue(meta, "hideInBreadcrumb", menu.getHideInBreadcrumb());
        putTrue(meta, "hideInTab", menu.getHideInTab());

        String target = normalizeBlank(menu.getComponent());
        if (TYPE_EMBEDDED.equals(menuType) && isExternalUrl(target)) {
            meta.put("iframeSrc", target);
        }
        if (TYPE_LINK.equals(menuType) && isExternalUrl(target)) {
            meta.put("link", target);
        }
        return meta;
    }

    private Collection<SysMenu> sortMenus(Collection<SysMenu> menus) {
        return menus.stream()
                .sorted(Comparator.comparing(SysMenu::getMenuGroup, Comparator.nullsLast(String::compareTo))
                        .thenComparing(SysMenu::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysMenu::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SysMenu::getId, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    private boolean isActive(SysMenu menu) {
        Short status = menu.getStatus() != null ? menu.getStatus() : menu.getVisible();
        return status == null || status == 1;
    }

    private String normalizeMenuType(String menuType) {
        return StringUtils.hasText(menuType) ? menuType.trim() : TYPE_MENU;
    }

    private String normalizePathForType(String path, String menuType) {
        return TYPE_BUTTON.equals(menuType) ? null : normalizeBlank(path);
    }

    private String normalizeComponentForType(String component, String menuType) {
        if (TYPE_BUTTON.equals(menuType) || TYPE_CATALOG.equals(menuType)) {
            return null;
        }
        return normalizeBlank(component);
    }

    private String normalizeRouteName(SysMenu menu) {
        if (StringUtils.hasText(menu.getMenuKey())) {
            return menu.getMenuKey().trim();
        }
        return menu.getId();
    }

    private String resolveRouteComponent(SysMenu menu, String menuType) {
        if (ROUTE_COMPONENT_TYPES.contains(menuType)) {
            return "IFrameView";
        }
        return normalizeBlank(menu.getComponent());
    }

    private String resolveRedirect(String parentPath, String firstChildPath) {
        if (!StringUtils.hasText(firstChildPath)) {
            return null;
        }
        if (firstChildPath.startsWith("/") || !StringUtils.hasText(parentPath)) {
            return firstChildPath;
        }
        if (parentPath.endsWith("/")) {
            return parentPath + firstChildPath;
        }
        return parentPath + "/" + firstChildPath;
    }

    private void putText(Map<String, Object> meta, String key, String value) {
        String normalized = normalizeBlank(value);
        if (normalized != null) {
            meta.put(key, normalized);
        }
    }

    private void putTrue(Map<String, Object> meta, String key, Short value) {
        if (value != null && value == 1) {
            meta.put(key, true);
        }
    }

    private boolean isExternalUrl(String value) {
        String normalized = normalizeBlank(value);
        return normalized != null
                && (normalized.startsWith("http://") || normalized.startsWith("https://"));
    }
}
