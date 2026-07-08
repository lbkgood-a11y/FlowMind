package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class CreateMenuRequest {
    private String parentId;
    private String menuKey;
    private String menuName;
    private String path;
    private String icon;
    private String menuGroup;
    private Integer sortOrder;
    private Boolean visible;
    private String permissionId;
    private String description;
}
