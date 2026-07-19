package com.triobase.service.action.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.util.ActionPayloadRedactor;
import com.triobase.service.action.entity.ActionExecution;
import com.triobase.service.action.mapper.ActionExecutionMapper;
import com.triobase.service.action.support.ActionJsonSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ActionAuditRecorder {

    private final ActionExecutionMapper actionExecutionMapper;
    private final ObjectMapper objectMapper;

    public String redactedPayloadSummary(ActionDefinition definition, GlobalActionRequest request) {
        Map<String, Object> redactedPayload = ActionPayloadRedactor.redact(
                request.getPayload(),
                definition.getSensitivePayloadPaths());
        return ActionJsonSupport.boundedJson(objectMapper, redactedPayload);
    }

    public void recordErrors(ActionExecution execution, List<ActionError> errors) {
        execution.setErrorSummary(ActionJsonSupport.boundedJson(objectMapper, errors));
        actionExecutionMapper.updateById(execution);
    }

    public void recordResult(ActionExecution execution, GlobalActionResult result) {
        execution.setOwnerService(firstNonBlank(result.getOwnerService(), execution.getOwnerService()));
        execution.setOwnerExecutionRef(firstNonBlank(result.getOwnerExecutionRef(), execution.getOwnerExecutionRef()));
        execution.setRetryable(result.isRetryable());
        execution.setResultSummary(ActionJsonSupport.boundedJson(objectMapper, result.getData()));
        execution.setErrorSummary(result.getErrors() == null || result.getErrors().isEmpty()
                ? null
                : ActionJsonSupport.boundedJson(objectMapper, result.getErrors()));
        actionExecutionMapper.updateById(execution);
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }
}
