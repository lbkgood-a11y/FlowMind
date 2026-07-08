package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_permission")
public class SysPermission {
    @TableId
    private String id;
    private String resource;
    private String action;
    private String description;
    private LocalDateTime createdAt;
}
