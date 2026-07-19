package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionEventType;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.service.action.entity.ActionDispatch;
import com.triobase.service.action.entity.ActionExecution;
import com.triobase.service.action.exception.ActionRuntimeException;
import com.triobase.service.action.repository.ActionEventRepository;
import com.triobase.service.action.repository.ActionExecutionRepository;
import com.triobase.service.action.support.ActionSecurityContextPropagator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ActionRuntimePipeline {

    private final ActionSecurityContextPropagator securityContextPropagator;
    private final ActionDefinitionRegistry definitionRegistry;
    private final ActionPayloadValidator payloadValidator;
    private final ActionPolicyChecker policyChecker;
    private final ActionIdempotencyGuard idempotencyGuard;
    private final ActionExecutionRepository executionRepository;
    private final ActionEventRepository eventRepository;
    private final ActionStatusService statusService;
    private final ActionAuditRecorder auditRecorder;
    private final ActionDispatchService dispatchService;
    private final ActionOwnerDispatcher ownerDispatcher;
    private final ActionResultFactory resultFactory;

    @Transactional
    public GlobalActionResult submit(GlobalActionRequest request) {
        GlobalActionRequest normalized = securityContextPropagator.propagate(request);
        ActionDefinition definition = definitionRegistry.require(normalized.getActionType());

        Optional<GlobalActionResult> duplicate = idempotencyGuard.duplicateResult(normalized);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }

        String payloadSummary = auditRecorder.redactedPayloadSummary(definition, normalized);
        ActionExecutionRepository.CreateResult createResult =
                executionRepository.createIfAbsent(normalized, payloadSummary);
        if (!createResult.created()) {
            return resultFactory.fromExecution(createResult.execution());
        }

        ActionExecution execution = createResult.execution();
        normalized.setActionId(execution.getId());
        eventRepository.append(execution, ActionEventType.CREATED, ActionStatus.CREATED,
                "ACTION_CREATED", Map.of());

        statusService.transition(execution, ActionStatus.VALIDATING);
        eventRepository.append(execution, ActionEventType.VALIDATION_STARTED,
                ActionStatus.VALIDATING, "ACTION_VALIDATION_STARTED", Map.of());

        List<ActionError> validationErrors = payloadValidator.validate(definition, normalized);
        if (!validationErrors.isEmpty()) {
            return reject(execution, validationErrors, ActionEventType.VALIDATION_FAILED,
                    "ACTION_VALIDATION_FAILED");
        }

        ActionPolicyDecision policyDecision = policyChecker.check(definition, normalized);
        if (!policyDecision.allowed()) {
            return reject(execution, policyDecision.errors(), ActionEventType.AUTHORIZATION_DENIED,
                    "ACTION_AUTHORIZATION_DENIED");
        }

        statusService.transition(execution, ActionStatus.AUTHORIZED);
        eventRepository.append(execution, ActionEventType.AUTHORIZATION_GRANTED,
                ActionStatus.AUTHORIZED, "ACTION_AUTHORIZATION_GRANTED", Map.of());

        ActionDispatch dispatch = dispatchService.createDispatch(execution, definition);
        dispatchService.markDispatched(dispatch);
        eventRepository.append(execution, ActionEventType.DISPATCHED,
                ActionStatus.AUTHORIZED, "ACTION_DISPATCHED",
                Map.of("ownerService", definition.getOwnerService()));

        try {
            GlobalActionResult ownerResult = ownerDispatcher.dispatch(definition, normalized, execution);
            return completeDispatch(definition, normalized, execution, dispatch, ownerResult);
        } catch (ActionRuntimeException exception) {
            return failDispatch(execution, dispatch, exception, exception.getCategory());
        } catch (Exception exception) {
            return failDispatch(execution, dispatch, exception, ActionErrorCategory.SYSTEM);
        }
    }

    private GlobalActionResult reject(ActionExecution execution,
                                      List<ActionError> errors,
                                      ActionEventType eventType,
                                      String message) {
        statusService.transition(execution, ActionStatus.REJECTED);
        auditRecorder.recordErrors(execution, errors);
        eventRepository.append(execution, eventType, ActionStatus.REJECTED, message,
                Map.of("errors", errors));
        GlobalActionResult result = resultFactory.fromExecution(execution);
        result.setMessage(message);
        result.getErrors().addAll(errors);
        return result;
    }

    private GlobalActionResult completeDispatch(ActionDefinition definition,
                                                GlobalActionRequest request,
                                                ActionExecution execution,
                                                ActionDispatch dispatch,
                                                GlobalActionResult ownerResult) {
        GlobalActionResult result = ownerResult != null ? ownerResult : new GlobalActionResult();
        ActionStatus status = normalizeOwnerStatus(definition, result.getStatus());
        statusService.transition(execution, status);
        enrichResult(definition, request, execution, result, status);
        auditRecorder.recordResult(execution, result);
        if (status == ActionStatus.FAILED || status == ActionStatus.REJECTED || status == ActionStatus.CANCELLED) {
            dispatchService.markFailed(dispatch, result.getMessage(), result.isRetryable());
        } else {
            dispatchService.markCompleted(dispatch);
        }
        eventRepository.append(execution, eventType(status), status,
                firstNonBlank(result.getMessage(), "ACTION_DISPATCH_COMPLETED"),
                Map.of("ownerService", definition.getOwnerService()));
        return result;
    }

    private GlobalActionResult failDispatch(ActionExecution execution,
                                            ActionDispatch dispatch,
                                            Exception exception,
                                            ActionErrorCategory category) {
        statusService.transition(execution, ActionStatus.FAILED);
        ActionError error = ActionError.of("ACTION_DISPATCH_FAILED", category, exception.getMessage());
        GlobalActionResult result = resultFactory.fromExecution(execution);
        result.setStatus(ActionStatus.FAILED);
        result.setMessage("ACTION_DISPATCH_FAILED");
        result.getErrors().add(error);
        result.setRetryable(category == ActionErrorCategory.DISPATCH || category == ActionErrorCategory.TIMEOUT);
        auditRecorder.recordResult(execution, result);
        dispatchService.markFailed(dispatch, exception.getMessage(), result.isRetryable());
        eventRepository.append(execution, ActionEventType.FAILED, ActionStatus.FAILED,
                "ACTION_DISPATCH_FAILED", Map.of("error", error));
        return result;
    }

    private ActionStatus normalizeOwnerStatus(ActionDefinition definition, ActionStatus status) {
        if (status != null && List.of(ActionStatus.ACCEPTED, ActionStatus.RUNNING,
                ActionStatus.SUCCEEDED, ActionStatus.FAILED, ActionStatus.REJECTED,
                ActionStatus.CANCELLED).contains(status)) {
            return status;
        }
        ActionExecutionMode mode = definition.getExecutionMode();
        if (mode == ActionExecutionMode.ASYNC
                || mode == ActionExecutionMode.WORKFLOW
                || mode == ActionExecutionMode.SIGNAL) {
            return ActionStatus.ACCEPTED;
        }
        return ActionStatus.SUCCEEDED;
    }

    private void enrichResult(ActionDefinition definition,
                              GlobalActionRequest request,
                              ActionExecution execution,
                              GlobalActionResult result,
                              ActionStatus status) {
        result.setActionId(execution.getId());
        result.setActionType(definition.getActionType());
        result.setStatus(status);
        if (result.getTarget() == null) {
            result.setTarget(request.getTarget());
        }
        result.setOwnerService(firstNonBlank(result.getOwnerService(), definition.getOwnerService()));
        result.setCreatedAt(result.getCreatedAt() != null ? result.getCreatedAt() : Instant.now());
        result.setUpdatedAt(Instant.now());
        if ((result.getRefreshScopes() == null || result.getRefreshScopes().isEmpty())
                && definition.getDefaultRefreshScopes() != null) {
            result.setRefreshScopes(definition.getDefaultRefreshScopes());
        }
        if (result.getTargetStatus() == null) {
            result.setTargetStatus(definition.getTargetStatus());
        }
        if (result.getTargetStatusGroup() == null) {
            result.setTargetStatusGroup(definition.getTargetStatusGroup());
        }
        result.getData().put("targetStatus", result.getTargetStatus());
        result.getData().put("targetStatusGroup", result.getTargetStatusGroup());
        result.getData().put("refreshScopes", result.getRefreshScopes());
        result.getData().put("ownerExecutionMetadata", result.getOwnerExecutionMetadata());
    }

    private ActionEventType eventType(ActionStatus status) {
        return switch (status) {
            case ACCEPTED -> ActionEventType.ACCEPTED;
            case RUNNING -> ActionEventType.RUNNING;
            case SUCCEEDED -> ActionEventType.SUCCEEDED;
            case REJECTED -> ActionEventType.AUTHORIZATION_DENIED;
            case CANCELLED -> ActionEventType.CANCELLED;
            default -> ActionEventType.FAILED;
        };
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }
}
