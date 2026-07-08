package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId
    private String id;
    private String username;
    private String password;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
