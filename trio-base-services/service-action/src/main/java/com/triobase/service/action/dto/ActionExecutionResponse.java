package com.triobase.service.action.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActionExecutionResponse {
    private String actionId;
    private String tenantId;
    private String actionType;
    private String source;
    private String actorType;
    private String actorId;
    private String actorName;
    private String targetType;
    private String targetId;
    private String targetOwnerService;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
