package com.triobase.service.workflow.executor;

import java.util.Map;

public record ClosureEffectContext(
        String tenantId,
        String closureId,
        String effectId,
        String businessType,
        String businessId,
        String effectKey,
        String triggerOutcome,
        Map<String, Object> parameters,
        String idempotencyKey,
        String traceId,
        String operatorId) {
}
