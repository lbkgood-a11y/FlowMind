package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_page_capability_target")
public class SysAuthPageCapabilityTarget extends BaseEntity {
    private String tenantId;
    private String capabilityId;
    private String resourceCode;
    private String actionCode;
    private String targetKind;
    private Short requiredFlag;
    private Short status;
}
