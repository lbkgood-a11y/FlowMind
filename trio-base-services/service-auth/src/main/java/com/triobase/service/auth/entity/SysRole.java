package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {
    @TableId
    private String id;
    private String roleCode;
    private String roleName;
    private String description;
    private LocalDateTime createdAt;
}
