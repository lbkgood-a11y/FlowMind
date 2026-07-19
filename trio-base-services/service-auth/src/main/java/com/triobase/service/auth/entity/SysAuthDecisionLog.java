package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_decision_log")
public class SysAuthDecisionLog extends BaseEntity {
    private String tenantId;
    private String userId;
    private String subjectSnapshot;
    private String resourceCode;
    private String actionCode;
    private Short allowed;
    private String reasonCodes;
    private String matchedGrantId;
    private String dataScopeSnapshot;
    private String fieldRuleSnapshot;
    private String guardSnapshot;
    private Long authVersion;
    private Long roleVersion;
    private Long dataPolicyVersion;
    private Long fieldPolicyVersion;
    private Long guardTemplateVersion;
    private String ownerService;
    private String businessObjectId;
    private String traceId;
    private String actionId;
    private String actionType;
    private String actionSource;
    private String actionTargetType;
    private String actionTargetId;
    private String actionCorrelationId;
    private String actionPayloadMetadata;
    private LocalDateTime decidedAt;
}
