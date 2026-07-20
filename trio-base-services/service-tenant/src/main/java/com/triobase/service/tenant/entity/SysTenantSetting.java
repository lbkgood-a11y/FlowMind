package com.triobase.service.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant_setting")
public class SysTenantSetting extends BaseEntity {
    private String tenantId;
    private String settingKey;
    private String settingValue;
    private String valueType;
    private Short sensitiveFlag;
    private Short status;
    private String description;
}
