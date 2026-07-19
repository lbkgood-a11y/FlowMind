package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {
    private String parentId;
    private String menuKey;
    private String menuName;
    private String path;
    private String component;
    private String icon;
    private String activeIcon;
    private String activePath;
    private String menuType;
    private String menuGroup;
    private Integer sortOrder;
    private Short visible;
    private Short status;
    private Short keepAlive;
    private Short affixTab;
    private Short hideInMenu;
    private Short hideChildrenInMenu;
    private Short hideInBreadcrumb;
    private Short hideInTab;
    private String badge;
    private String badgeType;
    private String badgeVariant;
    private String permissionCode;
    private String description;
}
