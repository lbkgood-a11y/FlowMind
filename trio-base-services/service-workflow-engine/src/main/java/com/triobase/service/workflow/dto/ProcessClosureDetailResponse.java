package com.triobase.service.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProcessClosureDetailResponse {
    private OutcomeItem outcome;
    private ClosureItem closure;
    private List<EffectItem> effects = new ArrayList<>();

    @Data
    public static class OutcomeItem {
        private String id;
        private String processInstanceId;
        private String processKey;
        private Integer processVersion;
        private String businessType;
        private String businessId;
        private String outcomeStatus;
        private String reason;
        private String tenantId;
        private String initiatorId;
        private String lastOperatorId;
        private String traceId;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ClosureItem {
        private String id;
        private String closureStatus;
        private String businessType;
        private String businessId;
        private String traceId;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
    }

    @Data
    public static class EffectItem {
        private String id;
        private String effectKey;
        private String effectType;
        private String triggerOutcome;
        private String businessActionCode;
        private String businessActionName;
        private String executorKey;
        private String mode;
        private String status;
        private String idempotencyKey;
        private String requestJson;
        private String resultJson;
        private String failureCategory;
        private String lastError;
        private Integer attemptCount;
        private LocalDateTime nextRetryAt;
        private String traceId;
        private boolean retryAvailable;
        private boolean manualHandlingAvailable;
    }
}
