package com.triobase.service.auth.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DecisionLogResponse {
    private String decisionId;
    private String tenantId;
    private String userId;
    private String resourceCode;
    private String actionCode;
    private Short allowed;
    private String reasonCodes;
    private String matchedGrantId;
    private Long authVersion;
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

    private String subjectSnapshot;
    private String dataScopeSnapshot;
    private String fieldRuleSnapshot;
    private String guardSnapshot;
}
