package com.triobase.service.action.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("act_action_execution")
public class ActionExecution extends BaseEntity {
    private String tenantId;
    private String actionType;
    private String source;
    private String actorType;
    private String actorId;
    private String actorName;
    private String targetType;
    private String targetId;
    private String targetOwnerService;
    private String targetTenantId;
    private String targetVersion;
    private String status;
    private String executionMode;
    private String auditLevel;
    private String idempotencyKey;
    private String correlationId;
    private String requestId;
    private String traceId;
    private String ownerService;
    private String ownerExecutionRef;
    private String payloadSummary;
    private String resultSummary;
    private String errorSummary;
    private Boolean retryable;
    private LocalDateTime completedAt;
}
