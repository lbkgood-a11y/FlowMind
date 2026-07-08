package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.CreateMenuRequest;
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
        if (!StringUtils.hasText(request.getMenuKey())
                || !StringUtils.hasText(request.getMenuName())
                || !StringUtils.hasText(request.getPath())) {
            throw new BizException(40031, "MENU_KEY_NAME_PATH_REQUIRED");
        }

        if (menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getMenuKey, request.getMenuKey())) > 0) {
            throw new BizException(40032, "MENU_KEY_ALREADY_EXISTS");
        }

        if (menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPath, request.getPath())) > 0) {
            throw new BizException(40033, "MENU_PATH_ALREADY_EXISTS");
        }

        if (StringUtils.hasText(request.getParentId()) && menuMapper.selectById(request.getParentId()) == null) {
            throw new BizException(40431, "PARENT_MENU_NOT_FOUND");
        }

        if (StringUtils.hasText(request.getPermissionId())) {
            SysPermission permission = permissionMapper.selectById(request.getPermissionId());
            if (permission == null) {
                throw new BizException(40432, "PERMISSION_NOT_FOUND");
            }
        }

        SysMenu menu = new SysMenu();
        menu.setId("M" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        menu.setParentId(StringUtils.hasText(request.getParentId()) ? request.getParentId() : null);
        menu.setMenuKey(request.getMenuKey());
        menu.setMenuName(request.getMenuName());
        menu.setPath(request.getPath());
        menu.setIcon(request.getIcon());
        menu.setMenuGroup(StringUtils.hasText(request.getMenuGroup()) ? request.getMenuGroup() : "general");
        menu.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        menu.setVisible(Boolean.FALSE.equals(request.getVisible()) ? (short) 0 : (short) 1);
        menu.setPermissionId(StringUtils.hasText(request.getPermissionId()) ? request.getPermissionId() : null);
        menu.setDescription(request.getDescription());
        menuMapper.insert(menu);
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
}
