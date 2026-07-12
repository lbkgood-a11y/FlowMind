package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_data_policy")
public class SysDataPolicy extends BaseEntity {
    private String tenantId;
    private String subjectType;
    private String subjectId;
    private String resourceCode;
    private String actionCode;
    private String effect;
    private String combineMode;
    private Short status;
    private String description;
}
