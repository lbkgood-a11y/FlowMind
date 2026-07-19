package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FormInstanceResponse {
    private String id;
    private String tenantId;
    private String formDefinitionId;
    private Integer formDefinitionVersion;
    private String schemaHash;
    private String formKey;
    private String status;
    private String dataJson;
    private String submittedBy;
    private String processKey;
    private String processInstanceId;
    private String workflowStatus;
    private LocalDateTime workflowBoundAt;
    private LocalDateTime workflowStatusUpdatedAt;
    private String processBindingTraceId;
    private String actionId;
    private String actionType;
    private String actionSource;
    private String actionActorType;
    private String actionActorId;
    private String actionActorName;
    private String actionTraceId;
    private String actionCorrelationId;
    private LocalDateTime submittedAt;
}
