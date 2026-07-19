package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class CreateMenuRequest {
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
    private Boolean visible;
    private Integer status;
    private Boolean keepAlive;
    private Boolean affixTab;
    private Boolean hideInMenu;
    private Boolean hideChildrenInMenu;
    private Boolean hideInBreadcrumb;
    private Boolean hideInTab;
    private String badge;
    private String badgeType;
    private String badgeVariant;
    private String permissionCode;
    private String description;
}
