package com.triobase.service.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bc_business_object")
public class BusinessObjectRecord extends BaseEntity {
    private String tenantId;
    private String objectType;
    private String displayName;
    private String ownerService;
    private String description;
    private Integer version;
    private String lifecycleStatus;
    private String manifestJson;
    private String statusesJson;
    private String actionsJson;
    private String fieldsJson;
    private String pageJson;
    private String attributesJson;
    @TableField("is_tenant_override")
    private Boolean tenantOverride;
    private LocalDateTime publishedAt;
    private LocalDateTime offlineAt;
}
