package com.triobase.service.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcessInstanceResponse {
    private String id;
    private String processPackageId;
    private String processKey;
    private String processName;
    private Integer version;
    private String title;
    private String status;
    private String formData;
    private String tenantId;
    private String businessType;
    private String businessId;
    private String launchMode;
    private String launchIdempotencyKey;
    private String initiatorId;
    private String initiatorName;
    private String currentNodeId;
    private String actionId;
    private String actionType;
    private String actionSource;
    private String actionActorType;
    private String actionActorId;
    private String actionActorName;
    private String actionTraceId;
    private String actionCorrelationId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
