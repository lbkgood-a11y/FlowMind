package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.CreateMenuRequest;
import com.triobase.service.auth.dto.UpdateMenuRequest;
import com.triobase.service.auth.entity.SysMenu;
import com.triobase.service.auth.entity.SysPermission;
import com.triobase.service.auth.mapper.MenuMapper;
import com.triobase.service.auth.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuService {

    private static final String DEFAULT_GROUP = "general";
    private static final String TYPE_BUTTON = "button";
    private static final String TYPE_MENU = "menu";

    private final MenuMapper menuMapper;
    private final PermissionMapper permissionMapper;

    public List<SysMenu> list() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getMenuGroup)
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getCreatedAt));
    }

    @Transactional
    public SysMenu create(CreateMenuRequest request) {
        validateRequired(request.getMenuKey(), request.getMenuName(), request.getPath(), request.getMenuType());
        validateParent(request.getParentId(), null);
        validatePermission(request.getPermissionId());
        validateUniqueMenuKey(request.getMenuKey(), null);
        validateUniquePath(request.getPath(), null);

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

        validateRequired(request.getMenuKey(), request.getMenuName(), request.getPath(), request.getMenuType());
        validateParent(request.getParentId(), id);
        validatePermission(request.getPermissionId());
        validateUniqueMenuKey(request.getMenuKey(), id);
        validateUniquePath(request.getPath(), id);

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
        menu.setParentId(normalizeBlank(request.getParentId()));
        menu.setMenuKey(request.getMenuKey().trim());
        menu.setMenuName(request.getMenuName().trim());
        menu.setPath(normalizeBlank(request.getPath()));
        menu.setComponent(normalizeBlank(request.getComponent()));
        menu.setIcon(normalizeBlank(request.getIcon()));
        menu.setActiveIcon(normalizeBlank(request.getActiveIcon()));
        menu.setActivePath(normalizeBlank(request.getActivePath()));
        menu.setMenuType(StringUtils.hasText(request.getMenuType()) ? request.getMenuType() : TYPE_MENU);
        menu.setMenuGroup(StringUtils.hasText(request.getMenuGroup()) ? request.getMenuGroup() : DEFAULT_GROUP);
        menu.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        menu.setStatus(toStatus(request.getStatus()));
        menu.setVisible(request.getVisible() == null ? menu.getStatus() : toShort(request.getVisible()));
        menu.setKeepAlive(toShort(request.getKeepAlive()));
        menu.setAffixTab(toShort(request.getAffixTab()));
        menu.setHideInMenu(toShort(request.getHideInMenu()));
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
        menu.setParentId(normalizeBlank(request.getParentId()));
        menu.setMenuKey(request.getMenuKey().trim());
        menu.setMenuName(request.getMenuName().trim());
        menu.setPath(normalizeBlank(request.getPath()));
        menu.setComponent(normalizeBlank(request.getComponent()));
        menu.setIcon(normalizeBlank(request.getIcon()));
        menu.setActiveIcon(normalizeBlank(request.getActiveIcon()));
        menu.setActivePath(normalizeBlank(request.getActivePath()));
        menu.setMenuType(StringUtils.hasText(request.getMenuType()) ? request.getMenuType() : TYPE_MENU);
        menu.setMenuGroup(StringUtils.hasText(request.getMenuGroup()) ? request.getMenuGroup() : DEFAULT_GROUP);
        menu.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        menu.setStatus(toStatus(request.getStatus()));
        menu.setVisible(request.getVisible() == null ? menu.getStatus() : toShort(request.getVisible()));
        menu.setKeepAlive(toShort(request.getKeepAlive()));
        menu.setAffixTab(toShort(request.getAffixTab()));
        menu.setHideInMenu(toShort(request.getHideInMenu()));
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

    private void validateRequired(String menuKey, String menuName, String path, String menuType) {
        if (!StringUtils.hasText(menuKey) || !StringUtils.hasText(menuName)) {
            throw new BizException(40031, "MENU_KEY_NAME_REQUIRED");
        }
        if (!TYPE_BUTTON.equals(menuType) && !StringUtils.hasText(path)) {
            throw new BizException(40035, "MENU_PATH_REQUIRED");
        }
    }

    private void validateParent(String parentId, String currentId) {
        if (!StringUtils.hasText(parentId)) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw new BizException(40036, "MENU_PARENT_CANNOT_BE_SELF");
        }
        if (menuMapper.selectById(parentId) == null) {
            throw new BizException(40431, "PARENT_MENU_NOT_FOUND");
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
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getMenuKey, menuKey);
        if (StringUtils.hasText(currentId)) {
            wrapper.ne(SysMenu::getId, currentId);
        }
        if (menuMapper.selectCount(wrapper) > 0) {
            throw new BizException(40032, "MENU_KEY_ALREADY_EXISTS");
        }
    }

    private void validateUniquePath(String path, String currentId) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPath, path);
        if (StringUtils.hasText(currentId)) {
            wrapper.ne(SysMenu::getId, currentId);
        }
        if (menuMapper.selectCount(wrapper) > 0) {
            throw new BizException(40033, "MENU_PATH_ALREADY_EXISTS");
        }
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
}
