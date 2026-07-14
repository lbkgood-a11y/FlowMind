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
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
