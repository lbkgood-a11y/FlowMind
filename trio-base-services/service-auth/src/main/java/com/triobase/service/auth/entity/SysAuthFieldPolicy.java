package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_field_policy")
public class SysAuthFieldPolicy extends BaseEntity {
    private String tenantId;
    private String subjectType;
    private String subjectId;
    private String resourceCode;
    private String fieldKey;
    private String readMode;
    private String writeMode;
    private String maskStrategy;
    private String effect;
    private Short status;
    private String description;
}
