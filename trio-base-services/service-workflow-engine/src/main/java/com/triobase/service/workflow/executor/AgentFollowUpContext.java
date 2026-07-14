package com.triobase.service.workflow.executor;

import java.util.Map;

public record AgentFollowUpContext(
        String tenantId,
        String businessType,
        String businessId,
        String agentActionCode,
        Map<String, Object> parameters,
        String idempotencyKey,
        String traceId,
        String operatorId,
        Map<String, Object> outcomePayload) {
}
