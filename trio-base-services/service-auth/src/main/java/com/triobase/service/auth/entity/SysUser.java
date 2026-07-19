package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String tenantId;
    private String username;
    private String password;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private String introduction;
    private Integer status;
}
