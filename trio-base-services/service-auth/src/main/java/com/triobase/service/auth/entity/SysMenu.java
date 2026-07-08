package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_menu")
public class SysMenu {
    @TableId
    private String id;
    private String parentId;
    private String menuKey;
    private String menuName;
    private String path;
    private String icon;
    private String menuGroup;
    private Integer sortOrder;
    private Short visible;
    private String permissionId;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
