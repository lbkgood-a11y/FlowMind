package com.triobase.service.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {
    private String tenantCode;
    private String tenantName;
    private String shortName;
    private String tenantType;
    private String status;
    private String isolationMode;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String region;
    private String timezone;
    private String locale;
    private String industry;
    private String planCode;
    private Integer maxUsers;
    private LocalDateTime expireAt;
    private String suspendedReason;
    private String attributesJson;
}
