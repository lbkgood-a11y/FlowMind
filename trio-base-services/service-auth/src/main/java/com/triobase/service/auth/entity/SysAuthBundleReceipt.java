package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_bundle_receipt")
public class SysAuthBundleReceipt extends BaseEntity {
    private String tenantId;
    private String idempotencyKey;
    private String roleId;
    private String applicationResourceCode;
    private String preset;
    private String requestHash;
    private Integer grantCount;
    private Long authorizationVersion;
}

