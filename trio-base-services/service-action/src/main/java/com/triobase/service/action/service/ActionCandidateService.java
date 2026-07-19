package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionConfirmation;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionAuditLevel;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.ActionCandidateValidationResult;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.service.action.exception.ActionRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionCandidateService {

    private final ActionDefinitionRegistry registry;
    private final ActionPayloadValidator payloadValidator;
    private final ActionPolicyChecker policyChecker;
    private final ActionOwnerGuardChecker ownerGuardChecker;
    private final ActionRuntimePipeline runtimePipeline;

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
        if (result.isSchemaValid()) {
            applyPolicyDecision(result, definition, request);
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
                    40048,
                    ActionErrorCategory.VALIDATION,
                    firstErrorCode(validation));
        }
        return runtimePipeline.submit(validation.getActionRequest());
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
        request.getTarget().setType(firstNonBlank(request.getTarget().getType(), definition.getTargetType()));
        request.getTarget().setOwnerService(firstNonBlank(request.getTarget().getOwnerService(),
                definition.getOwnerService()));
        if (request.getContext() == null) {
            request.setContext(new ActionContext());
        }
        return request;
    }

    private void applyPolicyDecision(ActionCandidateValidationResult result,
                                     ActionDefinition definition,
                                     GlobalActionRequest request) {
        ActionPolicyDecision decision = policyChecker.check(definition, request);
        AuthorizationDecisionResponse response = decision.response();
        if (response != null) {
            result.setVisible(result.isVisible() && response.isVisible());
            if (!response.isVisible()) {
                disable(result, firstNonBlank(response.getDisabledReason(), "ACTION_AUTHORIZATION_HIDDEN"));
            } else if (!response.isEnabled()) {
                disable(result, firstNonBlank(response.getDisabledReason(), "ACTION_AUTHORIZATION_DENIED"));
            }
        }
        if (decision.allowed()) {
            return;
        }
        List<ActionError> errors = decision.errors() != null ? decision.errors() : List.of();
        result.getErrors().addAll(errors);
        disable(result, errors.stream()
                .map(ActionError::getCode)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("ACTION_AUTHORIZATION_DENIED"));
    }

    private void applyOwnerGuardDecision(ActionCandidateValidationResult result,
                                         ActionDefinition definition,
                                         GlobalActionRequest request) {
        ActionOwnerGuardResponse guard = ownerGuardChecker.check(definition, request);
        if (guard == null || guard.isAllowed()) {
            return;
        }
        List<ActionError> errors = guard.getErrors() != null ? guard.getErrors() : List.of();
        result.getErrors().addAll(errors);
        disable(result, firstNonBlank(
                errors.stream()
                        .map(ActionError::getCode)
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse(null),
                guard.getMessage(),
                guard.getGuardCode(),
                "ACTION_OWNER_GUARD_DENIED"));
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
                && StringUtils.hasText(context.getConfirmationId())
                && StringUtils.hasText(context.getConfirmedBy());
    }

    private ActionCandidateValidationResult finalizeResult(ActionCandidateValidationResult result) {
        result.setValid(result.isDefinitionExists() && result.isSchemaValid());
        result.setDispatchable(result.isValid()
                && result.isEnabled()
                && (!result.isRequiresConfirmation() || result.isConfirmationSatisfied())
                && result.getActionRequest() != null);
        return result;
    }

    private void disable(ActionCandidateValidationResult result, String reason) {
        result.setEnabled(false);
        result.setDisabledReason(StringUtils.hasText(reason) ? reason : "ACTION_NOT_AVAILABLE");
    }

    private String firstErrorCode(ActionCandidateValidationResult validation) {
        return validation.getErrors().stream()
                .map(ActionError::getCode)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("ACTION_CANDIDATE_NOT_DISPATCHABLE");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
