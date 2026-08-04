package com.triobase.common.action.runtime;

import com.triobase.common.action.definition.ActionConfirmation;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionAuditLevel;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionEventType;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.ActionCandidateValidationResult;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.ActionEventPayload;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerExecutor;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.action.util.ActionCorrelationIds;
import com.triobase.common.core.util.StringHelpers;
import com.triobase.common.action.util.ActionPayloadRedactor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 在业务 Owner 进程内执行受治理的 Global Action。
 *
 * <p>运行顺序固定为定义注册、Schema 校验、确认校验、Owner Guard、幂等执行和审计。AI、LUI
 * 与外部编排只能提交 ActionCandidate/GlobalActionRequest，不能绕过本运行时直接调用任意 URL
 * 或写业务库。</p>
 */
public class OwnerHostedActionRuntime {

    private static final int ERROR_CANDIDATE_NOT_DISPATCHABLE = 40048;

    private final ActionDefinitionRegistry registry;
    private final ActionPayloadValidator payloadValidator;
    private final ActionOwnerExecutor executor;
    private final OwnerActionGuardEvaluator guardEvaluator;
    private final OwnerActionIdempotencyStore idempotencyStore;
    private final OwnerActionAuditSink auditSink;

    public OwnerHostedActionRuntime(ActionDefinitionRegistry registry,
                                    ActionPayloadValidator payloadValidator,
                                    ActionOwnerExecutor executor,
                                    OwnerActionGuardEvaluator guardEvaluator) {
        this(registry, payloadValidator, executor, guardEvaluator,
                NoopOwnerActionIdempotencyStore.INSTANCE,
                NoopOwnerActionAuditSink.INSTANCE);
    }

    public OwnerHostedActionRuntime(ActionDefinitionRegistry registry,
                                    ActionPayloadValidator payloadValidator,
                                    ActionOwnerExecutor executor,
                                    OwnerActionGuardEvaluator guardEvaluator,
                                    OwnerActionIdempotencyStore idempotencyStore,
                                    OwnerActionAuditSink auditSink) {
        this.registry = registry;
        this.payloadValidator = payloadValidator;
        this.executor = executor;
        this.guardEvaluator = guardEvaluator != null ? guardEvaluator : OwnerActionGuardEvaluator.allowAll();
        this.idempotencyStore = idempotencyStore != null
                ? idempotencyStore
                : NoopOwnerActionIdempotencyStore.INSTANCE;
        this.auditSink = auditSink != null ? auditSink : NoopOwnerActionAuditSink.INSTANCE;
    }

    public List<ActionDefinition> definitions() {
        return registry.all();
    }

    public ActionCandidateValidationResult validate(ActionCandidate candidate) {
        ActionCandidate actual = candidate != null ? candidate : new ActionCandidate();
        ActionCandidateValidationResult result = new ActionCandidateValidationResult();
        result.setCandidateId(actual.getCandidateId());
        result.setActionType(actual.getActionType());

        ActionDefinition definition = registry.find(actual.getActionType()).orElse(null);
        if (definition == null) {
            result.setVisible(false);
            result.setEnabled(false);
            result.setDisabledReason("ACTION_CANDIDATE_UNREGISTERED");
            result.getErrors().add(ActionError.of(
                    "ACTION_CANDIDATE_UNREGISTERED",
                    ActionErrorCategory.VALIDATION,
                    "ACTION_CANDIDATE_UNREGISTERED"));
            return finalizeResult(result);
        }

        result.setDefinitionExists(true);
        result.setVisible(definition.isVisibleByDefault());
        result.setDanger(definition.isDanger());
        result.setExecutionMode(definition.getExecutionMode());
        result.setTargetStatus(definition.getTargetStatus());
        result.setTargetStatusGroup(definition.getTargetStatusGroup());
        result.setRefreshScopes(definition.getDefaultRefreshScopes());
        GlobalActionRequest request = normalizeRequest(actual, definition);
        result.setActionRequest(request);

        List<ActionError> payloadErrors = payloadValidator.validate(definition, request);
        result.getErrors().addAll(payloadErrors);
        result.setSchemaValid(payloadErrors.isEmpty());
        if (!payloadErrors.isEmpty()) {
            disable(result, firstErrorCode(result));
        }

        ActionConfirmation confirmation = confirmation(actual, definition);
        boolean requiresConfirmation = requiresConfirmation(actual, definition, confirmation);
        result.setRequiresConfirmation(requiresConfirmation);
        result.setConfirmation(confirmation);
        result.setConfirmationSatisfied(!requiresConfirmation || confirmationSatisfied(request));
        if (requiresConfirmation && !result.isConfirmationSatisfied()) {
            result.getErrors().add(ActionError.of(
                    "ACTION_CONFIRMATION_REQUIRED",
                    ActionErrorCategory.SECURITY,
                    "ACTION_CONFIRMATION_REQUIRED"));
        }
        if (result.isSchemaValid() && result.isEnabled()) {
            applyOwnerGuardDecision(result, definition, request);
        }
        return finalizeResult(result);
    }

    public List<ActionCandidateValidationResult> validateBatch(List<ActionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream().map(this::validate).toList();
    }

    public GlobalActionResult dispatch(ActionCandidate candidate) {
        ActionCandidateValidationResult validation = validate(candidate);
        if (!validation.isDispatchable()) {
            throw new ActionRuntimeException(
                    ERROR_CANDIDATE_NOT_DISPATCHABLE,
                    ActionErrorCategory.VALIDATION,
                    firstErrorCode(validation));
        }
        return dispatch(validation.getActionRequest());
    }

    public GlobalActionResult dispatch(GlobalActionRequest request) {
        GlobalActionRequest normalized = normalizeRequest(request);
        ActionDefinition definition = registry.require(normalized.getActionType());
        normalized.setActionType(definition.getActionType());
        if (normalized.getSource() == null) {
            normalized.setSource(ActionSource.GUI);
        }
        if (normalized.getExecutionMode() == null) {
            normalized.setExecutionMode(definition.getExecutionMode());
        }
        normalized.getTarget().setType(StringHelpers.firstNonBlank(normalized.getTarget().getType(), definition.getTargetType()));
        normalized.getTarget().setOwnerService(StringHelpers.firstNonBlank(
                normalized.getTarget().getOwnerService(), definition.getOwnerService()));
        if (normalized.getActionId() == null || normalized.getActionId().isBlank()) {
            normalized.setActionId(ActionCorrelationIds.newActionId());
        }
        // 幂等存储包裹真正副作用；重复请求返回首次结果，但仍记录重复接收的审计证据。
        OwnerActionIdempotencyResult idempotencyResult = idempotencyStore.execute(
                normalized,
                () -> dispatchOnce(definition, normalized));
        if (idempotencyResult.duplicate()) {
            emitAuditEvent(definition, normalized, idempotencyResult.result(),
                    ActionEventType.ACCEPTED, "ACTION_IDEMPOTENT_DUPLICATE");
        }
        return idempotencyResult.result();
    }

    private GlobalActionResult dispatchOnce(ActionDefinition definition, GlobalActionRequest normalized) {
        List<ActionError> payloadErrors = payloadValidator.validate(definition, normalized);
        if (!payloadErrors.isEmpty()) {
            return rejected(definition, normalized, payloadErrors, "ACTION_VALIDATION_FAILED");
        }
        ActionOwnerGuardResponse guard = guardEvaluator.check(definition, normalized);
        if (guard != null && !guard.isAllowed()) {
            return rejected(definition, normalized,
                    guard.getErrors() != null ? guard.getErrors() : List.of(),
                    StringHelpers.firstNonBlank(guard.getMessage(), "ACTION_OWNER_GUARD_DENIED"));
        }
        if (executor == null) {
            return failed(definition, normalized, "ACTION_EXECUTOR_UNAVAILABLE",
                    ActionError.of("ACTION_EXECUTOR_UNAVAILABLE",
                            ActionErrorCategory.DISPATCH,
                            "ACTION_EXECUTOR_UNAVAILABLE"),
                    true);
        }
        try {
            GlobalActionResult ownerResult = executor.execute(normalized);
            GlobalActionResult result = ActionResultNormalizer.normalize(definition, normalized, ownerResult);
            emitAuditEvent(definition, normalized, result, eventType(result.getStatus()), result.getMessage());
            return result;
        } catch (ActionRuntimeException exception) {
            return failed(definition, normalized, exception.getMessage(),
                    ActionError.of("ACTION_DISPATCH_FAILED", exception.getCategory(), exception.getMessage()),
                    exception.getCategory() == ActionErrorCategory.DISPATCH
                            || exception.getCategory() == ActionErrorCategory.TIMEOUT);
        } catch (Exception exception) {
            return failed(definition, normalized, "ACTION_DISPATCH_FAILED",
                    ActionError.of("ACTION_DISPATCH_FAILED",
                            ActionErrorCategory.SYSTEM,
                            StringHelpers.firstNonBlank(exception.getMessage(), "ACTION_DISPATCH_FAILED")),
                    true);
        }
    }

    private GlobalActionRequest normalizeRequest(ActionCandidate candidate, ActionDefinition definition) {
        GlobalActionRequest request = candidate.toActionRequest();
        request.setActionType(definition.getActionType());
        request.setSource(candidate.getSource() != null ? candidate.getSource() : ActionSource.LUI);
        request.setExecutionMode(candidate.getExecutionMode() != null
                ? candidate.getExecutionMode()
                : definition.getExecutionMode());
        request.setPayload(candidate.getPayload() != null ? candidate.getPayload() : new LinkedHashMap<>());
        if (request.getTarget() == null) {
            request.setTarget(new ActionTarget());
        }
        request.getTarget().setType(StringHelpers.firstNonBlank(request.getTarget().getType(), definition.getTargetType()));
        request.getTarget().setOwnerService(StringHelpers.firstNonBlank(
                request.getTarget().getOwnerService(), definition.getOwnerService()));
        if (request.getContext() == null) {
            request.setContext(new ActionContext());
        }
        return request;
    }

    private GlobalActionRequest normalizeRequest(GlobalActionRequest request) {
        GlobalActionRequest normalized = request != null ? request : new GlobalActionRequest();
        if (normalized.getPayload() == null) {
            normalized.setPayload(new LinkedHashMap<>());
        }
        if (normalized.getContext() == null) {
            normalized.setContext(new ActionContext());
        }
        if (normalized.getTarget() == null) {
            normalized.setTarget(new ActionTarget());
        }
        return normalized;
    }

    private void applyOwnerGuardDecision(ActionCandidateValidationResult result,
                                         ActionDefinition definition,
                                         GlobalActionRequest request) {
        ActionOwnerGuardResponse guard = guardEvaluator.check(definition, request);
        if (guard == null || guard.isAllowed()) {
            return;
        }
        List<ActionError> errors = guard.getErrors() != null ? guard.getErrors() : List.of();
        result.getErrors().addAll(errors);
        disable(result, StringHelpers.firstNonBlank(
                errors.stream()
                        .map(ActionError::getCode)
                        .filter(this::hasText)
                        .findFirst()
                        .orElse(null),
                guard.getMessage(),
                guard.getGuardCode(),
                "ACTION_OWNER_GUARD_DENIED"));
    }

    private GlobalActionResult rejected(ActionDefinition definition,
                                        GlobalActionRequest request,
                                        List<ActionError> errors,
                                        String message) {
        GlobalActionResult result = new GlobalActionResult();
        result.setActionId(request.getActionId());
        result.setActionType(definition.getActionType());
        result.setTarget(request.getTarget());
        result.setOwnerService(definition.getOwnerService());
        result.setStatus(ActionStatus.REJECTED);
        result.setMessage(message);
        result.setErrors(errors != null ? errors : List.of());
        GlobalActionResult normalized = ActionResultNormalizer.normalize(definition, request, result);
        emitAuditEvent(definition, request, normalized,
                "ACTION_VALIDATION_FAILED".equals(message)
                        ? ActionEventType.VALIDATION_FAILED
                        : ActionEventType.AUTHORIZATION_DENIED,
                message);
        return normalized;
    }

    private GlobalActionResult failed(ActionDefinition definition,
                                      GlobalActionRequest request,
                                      String message,
                                      ActionError error,
                                      boolean retryable) {
        GlobalActionResult result = new GlobalActionResult();
        result.setActionId(request.getActionId());
        result.setActionType(definition.getActionType());
        result.setTarget(request.getTarget());
        result.setOwnerService(definition.getOwnerService());
        result.setStatus(ActionStatus.FAILED);
        result.setMessage(message);
        result.setRetryable(retryable);
        result.setErrors(error != null ? List.of(error) : List.of());
        GlobalActionResult normalized = ActionResultNormalizer.normalize(definition, request, result);
        emitAuditEvent(definition, request, normalized, ActionEventType.FAILED, message);
        return normalized;
    }

    private ActionConfirmation confirmation(ActionCandidate candidate, ActionDefinition definition) {
        if (candidate.getConfirmation() != null) {
            return candidate.getConfirmation();
        }
        return definition.getConfirmation();
    }

    private boolean requiresConfirmation(ActionCandidate candidate,
                                         ActionDefinition definition,
                                         ActionConfirmation confirmation) {
        return candidate.isRequiresConfirmation()
                || definition.isRequiresConfirmation()
                || definition.getAuditLevel() == ActionAuditLevel.SENSITIVE
                || definition.getAuditLevel() == ActionAuditLevel.CRITICAL
                || (confirmation != null && confirmation.isRequired());
    }

    private boolean confirmationSatisfied(GlobalActionRequest request) {
        ActionContext context = request.getContext();
        return context != null
                && hasText(context.getConfirmationId())
                && hasText(context.getConfirmedBy());
    }

    private ActionCandidateValidationResult finalizeResult(ActionCandidateValidationResult result) {
        result.setValid(result.isDefinitionExists() && result.isSchemaValid());
        result.setDispatchable(result.isValid()
                && result.isEnabled()
                && (!result.isRequiresConfirmation() || result.isConfirmationSatisfied())
                && result.getActionRequest() != null);
        return result;
    }

    private void emitAuditEvent(ActionDefinition definition,
                                GlobalActionRequest request,
                                GlobalActionResult result,
                                ActionEventType eventType,
                                String message) {
        if (result == null) {
            return;
        }
        ActionEventPayload event = new ActionEventPayload();
        event.setEventId("evt_" + UUID.randomUUID().toString().replace("-", ""));
        event.setActionId(result.getActionId());
        event.setEventType(eventType);
        event.setStatus(result.getStatus());
        event.setMessage(StringHelpers.firstNonBlank(message, result.getMessage(), eventType.name()));
        event.setOccurredAt(Instant.now());
        event.setData(auditData(definition, request, result));
        try {
            auditSink.emit(event);
        } catch (RuntimeException ignored) {
            // Audit sinks must not turn an already-executed owner side effect into a failed action result.
        }
    }

    private Map<String, Object> auditData(ActionDefinition definition,
                                          GlobalActionRequest request,
                                          GlobalActionResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        ActionContext context = request.getContext();
        data.put("actionType", definition.getActionType());
        data.put("source", request.getSource() != null ? request.getSource().name() : null);
        data.put("actor", actorSummary(request));
        data.put("target", targetSummary(request.getTarget()));
        data.put("ownerService", StringHelpers.firstNonBlank(result.getOwnerService(), definition.getOwnerService()));
        data.put("status", result.getStatus() != null ? result.getStatus().name() : null);
        data.put("idempotencyKey", request.getIdempotencyKey());
        data.put("traceId", context != null ? context.getTraceId() : null);
        data.put("correlationId", context != null ? context.getCorrelationId() : null);
        data.put("ownerExecutionRef", result.getOwnerExecutionRef());
        data.put("retryable", result.isRetryable());
        data.put("payloadSummary", ActionPayloadRedactor.redact(
                request.getPayload(),
                definition.getSensitivePayloadPaths()));
        data.put("resultSummary", ActionPayloadRedactor.boundedSummary(result.getData()));
        data.put("errorSummary", ActionPayloadRedactor.boundedSummary(result.getErrors()));
        return data;
    }

    private Map<String, Object> actorSummary(GlobalActionRequest request) {
        Map<String, Object> actor = new LinkedHashMap<>();
        if (request.getActor() == null) {
            return actor;
        }
        actor.put("type", request.getActor().getType() != null ? request.getActor().getType().name() : null);
        actor.put("id", request.getActor().getId());
        actor.put("displayName", request.getActor().getDisplayName());
        actor.put("tenantId", request.getActor().getTenantId());
        actor.put("delegatedBy", request.getActor().getDelegatedBy());
        actor.put("reason", ActionPayloadRedactor.boundedSummary(request.getActor().getReason()));
        return actor;
    }

    private Map<String, Object> targetSummary(ActionTarget target) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (target == null) {
            return summary;
        }
        summary.put("type", target.getType());
        summary.put("id", target.getId());
        summary.put("tenantId", target.getTenantId());
        summary.put("ownerService", target.getOwnerService());
        summary.put("version", target.getVersion());
        return summary;
    }

    private ActionEventType eventType(ActionStatus status) {
        if (status == null) {
            return ActionEventType.FAILED;
        }
        return switch (status) {
            case ACCEPTED -> ActionEventType.ACCEPTED;
            case RUNNING -> ActionEventType.RUNNING;
            case SUCCEEDED -> ActionEventType.SUCCEEDED;
            case REJECTED -> ActionEventType.AUTHORIZATION_DENIED;
            case CANCELLED -> ActionEventType.CANCELLED;
            default -> ActionEventType.FAILED;
        };
    }

    private void disable(ActionCandidateValidationResult result, String reason) {
        result.setEnabled(false);
        result.setDisabledReason(hasText(reason) ? reason : "ACTION_NOT_AVAILABLE");
    }

    private String firstErrorCode(ActionCandidateValidationResult validation) {
        return validation.getErrors().stream()
                .map(ActionError::getCode)
                .filter(this::hasText)
                .findFirst()
                .orElse("ACTION_CANDIDATE_NOT_DISPATCHABLE");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
