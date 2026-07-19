package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_guard_template")
public class SysAuthGuardTemplate extends BaseEntity {
    private String tenantId;
    private String guardCode;
    private String ownerService;
    private String supportedResourceTypes;
    private String configSchemaJson;
    private String description;
    private Short status;
}
