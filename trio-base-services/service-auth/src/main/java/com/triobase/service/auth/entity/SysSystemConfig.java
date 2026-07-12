package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_system_config")
public class SysSystemConfig extends BaseEntity {
    private String tenantId;
    private String configKey;
    private String configValue;
    private String defaultValue;
    private String configType;
    private String configGroup;
    private Short sensitive;
    private Short systemFlag;
    private Short status;
    private Integer sortOrder;
    private String description;
}
