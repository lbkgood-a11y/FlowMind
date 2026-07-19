package com.triobase.service.action.service;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.action.dto.ActionEventResponse;
import com.triobase.service.action.dto.ActionExecutionResponse;
import com.triobase.service.action.dto.ActionQueryCriteria;
import com.triobase.service.action.entity.ActionEvent;
import com.triobase.service.action.entity.ActionExecution;
import com.triobase.service.action.exception.ActionRuntimeException;
import com.triobase.service.action.repository.ActionEventRepository;
import com.triobase.service.action.repository.ActionExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionQueryService {

    private final ActionExecutionRepository executionRepository;
    private final ActionEventRepository eventRepository;

    public ActionExecutionResponse detail(String actionId) {
        return executionRepository.findById(actionId)
                .map(this::toExecutionResponse)
                .orElseThrow(() -> new ActionRuntimeException(
                        40442,
                        ActionErrorCategory.VALIDATION,
                        "ACTION_EXECUTION_NOT_FOUND",
                        "actionId",
                        null));
    }

    public List<ActionEventResponse> events(String actionId) {
        return eventRepository.findByActionId(actionId).stream()
                .map(this::toEventResponse)
                .toList();
    }

    public PageResult<ActionExecutionResponse> query(ActionQueryCriteria criteria) {
        return executionRepository.query(criteria).map(this::toExecutionResponse);
    }

    public ActionExecutionResponse toExecutionResponse(ActionExecution execution) {
        ActionExecutionResponse response = new ActionExecutionResponse();
        response.setActionId(execution.getId());
        response.setTenantId(execution.getTenantId());
        response.setActionType(execution.getActionType());
        response.setSource(execution.getSource());
        response.setActorType(execution.getActorType());
        response.setActorId(execution.getActorId());
        response.setActorName(execution.getActorName());
        response.setTargetType(execution.getTargetType());
        response.setTargetId(execution.getTargetId());
        response.setTargetOwnerService(execution.getTargetOwnerService());
        response.setStatus(execution.getStatus());
        response.setExecutionMode(execution.getExecutionMode());
        response.setAuditLevel(execution.getAuditLevel());
        response.setIdempotencyKey(execution.getIdempotencyKey());
        response.setCorrelationId(execution.getCorrelationId());
        response.setRequestId(execution.getRequestId());
        response.setTraceId(execution.getTraceId());
        response.setOwnerService(execution.getOwnerService());
        response.setOwnerExecutionRef(execution.getOwnerExecutionRef());
        response.setPayloadSummary(execution.getPayloadSummary());
        response.setResultSummary(execution.getResultSummary());
        response.setErrorSummary(execution.getErrorSummary());
        response.setRetryable(execution.getRetryable());
        response.setCompletedAt(execution.getCompletedAt());
        response.setCreatedAt(execution.getCreatedAt());
        response.setUpdatedAt(execution.getUpdatedAt());
        return response;
    }

    public ActionEventResponse toEventResponse(ActionEvent event) {
        ActionEventResponse response = new ActionEventResponse();
        response.setEventId(event.getId());
        response.setActionId(event.getActionId());
        response.setTenantId(event.getTenantId());
        response.setEventType(event.getEventType());
        response.setStatus(event.getStatus());
        response.setSequenceNo(event.getSequenceNo());
        response.setMessage(event.getMessage());
        response.setEventDataJson(event.getEventDataJson());
        response.setTraceId(event.getTraceId());
        response.setOccurredAt(event.getOccurredAt());
        return response;
    }
}
