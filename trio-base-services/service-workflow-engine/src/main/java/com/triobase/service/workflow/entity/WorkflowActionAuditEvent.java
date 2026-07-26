package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_action_audit_event")
public class WorkflowActionAuditEvent extends BaseEntity {
    private String eventId;
    private String tenantId;
    private String actionId;
    private String actionType;
    private String eventType;
    private String actionStatus;
    private String actionSource;
    private String actorType;
    private String actorId;
    private String actorName;
    private String targetType;
    private String targetId;
    private String targetOwnerService;
    private String ownerService;
    private String idempotencyKey;
    private String traceId;
    private String correlationId;
    private String ownerExecutionRef;
    private String message;
    private String eventDataJson;
    private LocalDateTime occurredAt;
}
