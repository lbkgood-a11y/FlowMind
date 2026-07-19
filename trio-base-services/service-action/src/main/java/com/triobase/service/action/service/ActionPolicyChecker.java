package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.dto.authz.AuthzDecisionReason;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ActionPolicyChecker {

    private static final String GLOBAL_TENANT = "GLOBAL";

    private final ActionAuthorizationClient authorizationClient;

    public ActionPolicyDecision check(ActionDefinition definition, GlobalActionRequest request) {
        if (definition.getRequiredPermission() == null || definition.getRequiredPermission().isBlank()) {
            return ActionPolicyDecision.allowed(null);
        }
        AuthorizationDecisionRequest decisionRequest = toDecisionRequest(definition, request);
        AuthorizationDecisionResponse response = authorizationClient.decide(decisionRequest);
        if (response != null && response.isAllowed()) {
            return ActionPolicyDecision.allowed(response);
        }
        return ActionPolicyDecision.denied(response, denialErrors(response));
    }

    private AuthorizationDecisionRequest toDecisionRequest(ActionDefinition definition, GlobalActionRequest request) {
        ActionActor actor = request.getActor();
        ActionTarget target = request.getTarget();
        ActionContext context = request.getContext();

        AuthorizationDecisionRequest decisionRequest = new AuthorizationDecisionRequest();
        decisionRequest.setTenantId(firstNonBlank(
                context != null ? context.getTenantId() : null,
                target != null ? target.getTenantId() : null,
                actor != null ? actor.getTenantId() : null,
                GLOBAL_TENANT));
        decisionRequest.setUserId(actor != null ? actor.getId() : null);
        decisionRequest.setResourceCode(firstNonBlank(
                target != null ? target.getType() : null,
                definition.getTargetType(),
                definition.getActionType()));
        decisionRequest.setActionCode(definition.getRequiredPermission());
        decisionRequest.setOwnerService(definition.getOwnerService());
        decisionRequest.setBusinessObjectId(target != null ? target.getId() : null);
        decisionRequest.setActionId(request.getActionId());
        decisionRequest.setActionType(definition.getActionType());
        decisionRequest.setActionSource(request.getSource() != null ? request.getSource().name() : null);
        decisionRequest.setActionTargetType(target != null ? target.getType() : definition.getTargetType());
        decisionRequest.setActionTargetId(target != null ? target.getId() : null);
        decisionRequest.setActionCorrelationId(context != null ? context.getCorrelationId() : null);
        Map<String, Object> payloadMetadata = new LinkedHashMap<>();
        payloadMetadata.put("payloadKeys",
                request.getPayload() != null ? List.copyOf(request.getPayload().keySet()) : List.of());
        payloadMetadata.put("targetOwnerService", target != null ? target.getOwnerService() : null);
        decisionRequest.setActionPayloadMetadata(payloadMetadata);
        decisionRequest.setEnforcementMode(true);
        decisionRequest.setAttributes(actionAttributes(definition, request));
        return decisionRequest;
    }

    private Map<String, Object> actionAttributes(ActionDefinition definition, GlobalActionRequest request) {
        ActionTarget target = request.getTarget();
        ActionContext context = request.getContext();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actionType", definition.getActionType());
        attributes.put("source", request.getSource() != null ? request.getSource().name() : null);
        attributes.put("targetType", target != null ? target.getType() : null);
        attributes.put("targetId", target != null ? target.getId() : null);
        attributes.put("targetOwnerService", target != null ? target.getOwnerService() : null);
        attributes.put("traceId", context != null ? context.getTraceId() : null);
        attributes.put("correlationId", context != null ? context.getCorrelationId() : null);
        attributes.put("payloadKeys", request.getPayload() != null ? List.copyOf(request.getPayload().keySet()) : List.of());
        attributes.put("guardRequirements", definition.getRequiredGuards());
        return attributes;
    }

    private List<ActionError> denialErrors(AuthorizationDecisionResponse response) {
        if (response == null || response.getReasons() == null || response.getReasons().isEmpty()) {
            return List.of(ActionError.of("ACTION_AUTHZ_DENIED",
                    ActionErrorCategory.AUTHORIZATION,
                    "ACTION_AUTHORIZATION_DENIED"));
        }
        return response.getReasons().stream()
                .map(this::toActionError)
                .toList();
    }

    private ActionError toActionError(AuthzDecisionReason reason) {
        ActionError error = ActionError.of(
                firstNonBlank(reason.getCode(), "ACTION_AUTHZ_DENIED"),
                ActionErrorCategory.AUTHORIZATION,
                firstNonBlank(reason.getMessage(), "ACTION_AUTHORIZATION_DENIED"));
        error.getDetails().put("source", reason.getSource());
        error.getDetails().put("evidenceId", reason.getEvidenceId());
        return error;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
