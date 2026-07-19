package com.triobase.service.action.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionConfirmation;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionAuditLevel;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.ActionCandidateValidationResult;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.service.action.exception.ActionRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionCandidateServiceTest {

    private ActionRuntimePipeline runtimePipeline;
    private ActionPolicyChecker policyChecker;
    private ActionOwnerGuardChecker ownerGuardChecker;
    private ActionCandidateService service;

    @BeforeEach
    void setUp() {
        runtimePipeline = mock(ActionRuntimePipeline.class);
        policyChecker = mock(ActionPolicyChecker.class);
        ownerGuardChecker = mock(ActionOwnerGuardChecker.class);
        ActionDefinitionRegistry registry = new ActionDefinitionRegistry(List.of());
        registry.register(definition(false, ActionAuditLevel.NORMAL));
        registry.register(definition(true, ActionAuditLevel.CRITICAL));
        service = new ActionCandidateService(
                registry,
                new ActionPayloadValidator(new ObjectMapper()),
                policyChecker,
                ownerGuardChecker,
                runtimePipeline);
        when(policyChecker.check(any(ActionDefinition.class), any(GlobalActionRequest.class)))
                .thenReturn(ActionPolicyDecision.allowed(null));
        when(ownerGuardChecker.check(any(ActionDefinition.class), any(GlobalActionRequest.class)))
                .thenReturn(ActionOwnerGuardResponse.allowed("TEST_ALLOWED"));
    }

    @Test
    void validatesRegisteredCandidateAndNormalizesActionRequest() {
        ActionCandidateValidationResult result = service.validate(candidate("process.task.approve",
                Map.of("taskId", "task-1")));

        assertThat(result.isValid()).isTrue();
        assertThat(result.isDispatchable()).isTrue();
        assertThat(result.getActionRequest().getSource()).isEqualTo(ActionSource.LUI);
        assertThat(result.getActionRequest().getExecutionMode()).isEqualTo(ActionExecutionMode.SIGNAL);
        assertThat(result.getActionRequest().getTarget().getOwnerService()).isEqualTo("service-workflow-engine");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getTargetStatus()).isEqualTo("APPROVED");
        assertThat(result.getTargetStatusGroup()).isEqualTo("TERMINAL");
        assertThat(result.getRefreshScopes()).containsExactly("task", "actions", "timeline");
    }

    @Test
    void rejectsUnregisteredCandidateBeforeDispatch() {
        ActionCandidateValidationResult result = service.validate(candidate("unknown.action",
                Map.of("taskId", "task-1")));

        assertThat(result.isDefinitionExists()).isFalse();
        assertThat(result.isDispatchable()).isFalse();
        assertThat(result.isVisible()).isFalse();
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getErrors()).extracting("code")
                .contains("ACTION_CANDIDATE_UNREGISTERED");
    }

    @Test
    void reportsPayloadSchemaErrors() {
        ActionCandidateValidationResult result = service.validate(candidate("process.task.approve",
                Map.of("unexpected", "value")));

        assertThat(result.isSchemaValid()).isFalse();
        assertThat(result.isDispatchable()).isFalse();
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getDisabledReason()).isEqualTo("ACTION_PAYLOAD_REQUIRED_MISSING");
        assertThat(result.getErrors()).extracting("code")
                .contains("ACTION_PAYLOAD_REQUIRED_MISSING", "ACTION_PAYLOAD_ADDITIONAL_PROPERTY");
    }

    @Test
    void reportsAuthorizationDenialAsDisabledAvailability() {
        ActionError denied = ActionError.of("AUTHZ_DENIED", ActionErrorCategory.AUTHORIZATION, "denied");
        when(policyChecker.check(any(ActionDefinition.class), any(GlobalActionRequest.class)))
                .thenReturn(ActionPolicyDecision.denied(null, List.of(denied)));

        ActionCandidateValidationResult result = service.validate(candidate("process.task.approve",
                Map.of("taskId", "task-1")));

        assertThat(result.isValid()).isTrue();
        assertThat(result.isDispatchable()).isFalse();
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getDisabledReason()).isEqualTo("AUTHZ_DENIED");
        assertThat(result.toAvailability().getErrors()).extracting(ActionError::getCode)
                .contains("AUTHZ_DENIED");
    }

    @Test
    void appliesAuthorizationRenderingMetadata() {
        AuthorizationDecisionResponse response = new AuthorizationDecisionResponse();
        response.setAllowed(true);
        response.setVisible(false);
        response.setEnabled(false);
        response.setDisabledReason("AUTHZ_ACTION_HIDDEN");
        when(policyChecker.check(any(ActionDefinition.class), any(GlobalActionRequest.class)))
                .thenReturn(ActionPolicyDecision.allowed(response));

        ActionCandidateValidationResult result = service.validate(candidate("process.task.approve",
                Map.of("taskId", "task-1")));

        assertThat(result.isVisible()).isFalse();
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getDisabledReason()).isEqualTo("AUTHZ_ACTION_HIDDEN");
    }

    @Test
    void appliesOwnerGuardDenialAsDisabledAvailability() {
        ActionError denied = ActionError.of(
                "PROCESS_TASK_ALREADY_COMPLETED",
                ActionErrorCategory.VALIDATION,
                "PROCESS_TASK_ALREADY_COMPLETED");
        when(ownerGuardChecker.check(any(ActionDefinition.class), any(GlobalActionRequest.class)))
                .thenReturn(ActionOwnerGuardResponse.denied(
                        "PROCESS_TASK_ACTIONABLE",
                        "PROCESS_TASK_ALREADY_COMPLETED",
                        List.of(denied)));

        ActionCandidateValidationResult result = service.validate(candidate("process.task.approve",
                Map.of("taskId", "task-1")));

        assertThat(result.isValid()).isTrue();
        assertThat(result.isDispatchable()).isFalse();
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getDisabledReason()).isEqualTo("PROCESS_TASK_ALREADY_COMPLETED");
    }

    @Test
    void validatesBatchCandidates() {
        List<ActionCandidateValidationResult> results = service.validateBatch(List.of(
                candidate("process.task.approve", Map.of("taskId", "task-1")),
                candidate("unknown.action", Map.of())));

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().isDispatchable()).isTrue();
        assertThat(results.get(1).isEnabled()).isFalse();
    }

    @Test
    void requiresConfirmationForCriticalActions() {
        ActionCandidateValidationResult result = service.validate(candidate("process.task.reject",
                Map.of("taskId", "task-1")));

        assertThat(result.isValid()).isTrue();
        assertThat(result.isRequiresConfirmation()).isTrue();
        assertThat(result.isDispatchable()).isFalse();
        assertThat(result.getErrors()).extracting("code")
                .contains("ACTION_CONFIRMATION_REQUIRED");
    }

    @Test
    void dispatchesOnlyAfterConfirmationIsSatisfied() {
        ActionCandidate candidate = candidate("process.task.reject", Map.of("taskId", "task-1"));
        candidate.getContext().setConfirmationId("confirm-1");
        candidate.getContext().setConfirmedBy("user-1");
        GlobalActionResult expected = new GlobalActionResult();
        expected.setStatus(ActionStatus.SUCCEEDED);
        when(runtimePipeline.submit(any(GlobalActionRequest.class))).thenReturn(expected);

        GlobalActionResult result = service.dispatch(candidate);

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        verify(runtimePipeline).submit(any(GlobalActionRequest.class));
    }

    @Test
    void dispatchRejectsNonDispatchableCandidate() {
        assertThatThrownBy(() -> service.dispatch(candidate("process.task.reject",
                Map.of("taskId", "task-1"))))
                .isInstanceOf(ActionRuntimeException.class)
                .hasMessage("ACTION_CONFIRMATION_REQUIRED");
    }

    private ActionCandidate candidate(String actionType, Map<String, Object> payload) {
        ActionCandidate candidate = new ActionCandidate();
        candidate.setActionType(actionType);
        candidate.getTarget().setId("task-1");
        candidate.setPayload(payload);
        return candidate;
    }

    private ActionDefinition definition(boolean critical, ActionAuditLevel auditLevel) {
        ActionDefinition definition = new ActionDefinition();
        definition.setActionType(critical ? "process.task.reject" : "process.task.approve");
        definition.setOwnerService("service-workflow-engine");
        definition.setTargetType("PROCESS_TASK");
        definition.setExecutionMode(ActionExecutionMode.SIGNAL);
        definition.setAuditLevel(auditLevel);
        definition.setTargetStatus(critical ? "REJECTED" : "APPROVED");
        definition.setTargetStatusGroup("TERMINAL");
        definition.getDefaultRefreshScopes().addAll(List.of("task", "actions", "timeline"));
        definition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["taskId"],
                  "additionalProperties": false,
                  "properties": {
                    "taskId": {"type": "string"}
                  }
                }
                """);
        if (critical) {
            ActionConfirmation confirmation = new ActionConfirmation();
            confirmation.setRequired(true);
            confirmation.setRiskLevel(ActionAuditLevel.CRITICAL);
            definition.setConfirmation(confirmation);
        }
        return definition;
    }
}
