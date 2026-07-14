package com.triobase.service.workflow.executor;

import java.util.Map;

public record BusinessActionContext(
        String tenantId,
        String businessType,
        String businessId,
        String actionCode,
        Map<String, Object> parameters,
        String idempotencyKey,
        String traceId,
        String operatorId) {
}
