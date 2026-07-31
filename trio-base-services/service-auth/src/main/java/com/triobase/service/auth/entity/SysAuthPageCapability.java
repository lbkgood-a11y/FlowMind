package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_page_capability")
public class SysAuthPageCapability extends BaseEntity {
    private String tenantId;
    private String catalogId;
    private String menuId;
    private String pageCode;
    private String pageName;
    private String capabilityCode;
    private String capabilityName;
    private String capabilityCategory;
    private String helpText;
    private String readinessStatus;
    private String readinessMessage;
    private Short scopeSupported;
    private Short fieldPolicySupported;
    private Integer sortOrder;
    private Short status;
    private String metadataJson;
}
