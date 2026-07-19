package com.triobase.service.action.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.service.action.entity.ActionExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ActionResultFactory {

    private final ObjectMapper objectMapper;

    public GlobalActionResult fromExecution(ActionExecution execution) {
        GlobalActionResult result = new GlobalActionResult();
        result.setActionId(execution.getId());
        result.setActionType(execution.getActionType());
        result.setStatus(ActionStatus.valueOf(execution.getStatus()));
        result.setTarget(target(execution));
        result.setOwnerService(execution.getOwnerService());
        result.setOwnerExecutionRef(execution.getOwnerExecutionRef());
        result.setRetryable(Boolean.TRUE.equals(execution.getRetryable()));
        result.setMessage(firstNonBlank(execution.getErrorSummary(), execution.getResultSummary()));
        result.setCreatedAt(toInstant(execution.getCreatedAt()));
        result.setUpdatedAt(toInstant(execution.getUpdatedAt()));
        restoreResultData(result, execution.getResultSummary());
        restoreErrors(result, execution.getErrorSummary());
        result.getData().put("payloadSummary", execution.getPayloadSummary());
        result.getData().put("resultSummary", execution.getResultSummary());
        result.getData().put("errorSummary", execution.getErrorSummary());
        result.getData().put("traceId", execution.getTraceId());
        result.getData().put("correlationId", execution.getCorrelationId());
        result.getData().put("idempotencyKey", execution.getIdempotencyKey());
        return result;
    }

    private void restoreResultData(GlobalActionResult result, String resultSummary) {
        if (resultSummary == null || resultSummary.isBlank()) {
            return;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(resultSummary, new TypeReference<>() {
            });
            result.getData().putAll(data);
        } catch (Exception ignored) {
            // Result summaries are bounded for audit. If a summary is truncated, keep the raw summary fields.
        }
    }

    private void restoreErrors(GlobalActionResult result, String errorSummary) {
        if (errorSummary == null || errorSummary.isBlank()) {
            return;
        }
        try {
            List<ActionError> errors = objectMapper.readValue(errorSummary, new TypeReference<>() {
            });
            result.getErrors().addAll(errors);
        } catch (Exception ignored) {
            // Error summaries may also be bounded; invalid JSON should not break status lookup.
        }
    }

    private ActionTarget target(ActionExecution execution) {
        ActionTarget target = new ActionTarget();
        target.setType(execution.getTargetType());
        target.setId(execution.getTargetId());
        target.setOwnerService(execution.getTargetOwnerService());
        target.setTenantId(execution.getTargetTenantId());
        target.setVersion(execution.getTargetVersion());
        return target;
    }

    private Instant toInstant(LocalDateTime value) {
        return value != null ? value.toInstant(ZoneOffset.UTC) : null;
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }
}
