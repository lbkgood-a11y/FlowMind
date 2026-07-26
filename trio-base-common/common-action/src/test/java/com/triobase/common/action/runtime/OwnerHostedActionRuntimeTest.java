package com.triobase.common.action.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionConfirmation;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.definition.ActionSensitivePath;
import com.triobase.common.action.enums.ActionAuditLevel;
import com.triobase.common.action.enums.ActionEventType;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.ActionCandidateValidationResult;
import com.triobase.common.action.model.ActionEventPayload;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerHostedActionRuntimeTest {

    private final ActionDefinition definition = definition();
    private final OwnerHostedActionRuntime runtime = new OwnerHostedActionRuntime(
            new ActionDefinitionRegistry(List.of(() -> List.of(definition))),
            new ActionPayloadValidator(new ObjectMapper()),
            new TestExecutor(),
            OwnerActionGuardEvaluator.allowAll());

    @Test
    void rejectsCandidateBeforeRequiredConfirmation() {
        ActionCandidate candidate = candidate();

        ActionCandidateValidationResult result = runtime.validate(candidate);

        assertThat(result.isDefinitionExists()).isTrue();
        assertThat(result.isSchemaValid()).isTrue();
        assertThat(result.isRequiresConfirmation()).isTrue();
        assertThat(result.isDispatchable()).isFalse();
        assertThat(result.getErrors()).extracting("code").contains("ACTION_CONFIRMATION_REQUIRED");
    }

    @Test
    void dispatchesConfirmedCandidateThroughLocalExecutor() {
        ActionCandidate candidate = candidate();
        candidate.getContext().setConfirmationId("confirm-1");
        candidate.getContext().setConfirmedBy("user-1");

        GlobalActionResult result = runtime.dispatch(candidate);

        assertThat(result.getActionId()).startsWith("act_");
        assertThat(result.getActionType()).isEqualTo("lowcode.form.submit");
        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(result.getOwnerService()).isEqualTo("service-lowcode");
        assertThat(result.getRefreshScopes()).containsExactly("document", "actions");
        assertThat(result.getData()).containsEntry("instanceId", "leave-1");
    }

    @Test
    void validatesPayloadSchema() {
        ActionCandidate candidate = candidate();
        candidate.setPayload(Map.of("appKey", "leave"));

        ActionCandidateValidationResult result = runtime.validate(candidate);

        assertThat(result.isSchemaValid()).isFalse();
        assertThat(result.getErrors()).extracting("code")
                .contains("ACTION_PAYLOAD_REQUIRED_MISSING");
    }

    @Test
    void duplicateIdempotencyKeyReturnsFirstResultWithoutRepeatingOwnerSideEffect() {
        CountingExecutor executor = new CountingExecutor();
        OwnerHostedActionRuntime idempotentRuntime = runtime(
                executor,
                OwnerActionGuardEvaluator.allowAll(),
                new InMemoryOwnerActionAuditSink());
        ActionCandidate candidate = confirmedCandidate();
        candidate.setIdempotencyKey(" submit-leave-1 ");
        candidate.getContext().setTenantId("tenant-a");

        GlobalActionResult first = idempotentRuntime.dispatch(candidate);
        GlobalActionResult duplicate = idempotentRuntime.dispatch(candidate);

        assertThat(executor.count()).isEqualTo(1);
        assertThat(duplicate.getActionId()).isEqualTo(first.getActionId());
        assertThat(duplicate.getData()).containsEntry("instanceId", "leave-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void emitsRedactedAuditEventForOwnerDispatchResult() {
        InMemoryOwnerActionAuditSink auditSink = new InMemoryOwnerActionAuditSink();
        OwnerHostedActionRuntime auditableRuntime = runtime(
                new TestExecutor(),
                OwnerActionGuardEvaluator.allowAll(),
                auditSink);
        ActionCandidate candidate = confirmedCandidate();
        candidate.setPayload(Map.of(
                "appKey", "leave",
                "actionCode", "submitAndLaunch",
                "data", Map.of(
                        "reason", "family",
                        "secret", "raw-secret-value")));

        auditableRuntime.dispatch(candidate);

        ActionEventPayload event = auditSink.events().stream()
                .filter(actual -> actual.getEventType() == ActionEventType.SUCCEEDED)
                .findFirst()
                .orElseThrow();
        Map<String, Object> payloadSummary = (Map<String, Object>) event.getData().get("payloadSummary");
        Map<String, Object> data = (Map<String, Object>) payloadSummary.get("data");
        assertThat(data).containsEntry("reason", "family");
        assertThat(data).containsEntry("secret", "***REDACTED***");
        assertThat(event.getData()).containsEntry("actionType", "lowcode.form.submit");
    }

    @Test
    void enforcesStatusTransitions() {
        assertThat(ActionStatusMachine.canTransition(ActionStatus.CREATED, ActionStatus.VALIDATING)).isTrue();
        assertThat(ActionStatusMachine.canTransition(ActionStatus.SUCCEEDED, ActionStatus.RUNNING)).isFalse();
        assertThatThrownBy(() -> ActionStatusMachine.requireTransition(ActionStatus.SUCCEEDED, ActionStatus.RUNNING))
                .isInstanceOf(ActionRuntimeException.class)
                .hasMessage("ACTION_STATUS_TERMINAL");
    }

    private OwnerHostedActionRuntime runtime(ActionOwnerExecutor executor,
                                             OwnerActionGuardEvaluator guardEvaluator,
                                             OwnerActionAuditSink auditSink) {
        return new OwnerHostedActionRuntime(
                new ActionDefinitionRegistry(List.of(() -> List.of(definition))),
                new ActionPayloadValidator(new ObjectMapper()),
                executor,
                guardEvaluator,
                new InMemoryOwnerActionIdempotencyStore(),
                auditSink);
    }

    private ActionCandidate confirmedCandidate() {
        ActionCandidate candidate = candidate();
        candidate.getContext().setConfirmationId("confirm-1");
        candidate.getContext().setConfirmedBy("user-1");
        return candidate;
    }

    private ActionCandidate candidate() {
        ActionCandidate candidate = new ActionCandidate();
        candidate.setActionType("lowcode.form.submit");
        candidate.setSource(ActionSource.LUI);
        candidate.getActor().setId("user-1");
        candidate.getTarget().setType("LOWCODE_FORM");
        candidate.getTarget().setOwnerService("service-lowcode");
        candidate.setPayload(Map.of(
                "appKey", "leave",
                "actionCode", "submitAndLaunch",
                "data", Map.of("reason", "family")));
        return candidate;
    }

    private ActionDefinition definition() {
        ActionDefinition definition = new ActionDefinition();
        definition.setActionType("lowcode.form.submit");
        definition.setOwnerService("service-lowcode");
        definition.setTargetType("LOWCODE_FORM");
        definition.setExecutionMode(ActionExecutionMode.SYNC);
        definition.setAuditLevel(ActionAuditLevel.SENSITIVE);
        definition.setConfirmation(confirmation());
        definition.getDefaultRefreshScopes().addAll(List.of("document", "actions"));
        definition.getSensitivePayloadPaths().add(ActionSensitivePath.of("data.secret"));
        definition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["appKey", "actionCode", "data"],
                  "additionalProperties": false,
                  "properties": {
                    "appKey": {"type": "string"},
                    "actionCode": {"type": "string"},
                    "data": {"type": "object"}
                  }
                }
                """);
        return definition;
    }

    private ActionConfirmation confirmation() {
        ActionConfirmation confirmation = new ActionConfirmation();
        confirmation.setRequired(true);
        confirmation.setTitle("submit");
        return confirmation;
    }

    private static final class TestExecutor implements ActionOwnerExecutor {

        @Override
        public String actionType() {
            return "lowcode.*";
        }

        @Override
        public GlobalActionResult execute(GlobalActionRequest request) {
            GlobalActionResult result = new GlobalActionResult();
            result.setActionId(request.getActionId());
            result.setActionType(request.getActionType());
            result.setOwnerService("service-lowcode");
            result.setStatus(ActionStatus.SUCCEEDED);
            result.setOwnerExecutionRef("leave-1");
            result.setData(Map.of("instanceId", "leave-1"));
            return result;
        }
    }

    private static final class CountingExecutor implements ActionOwnerExecutor {

        private final TestExecutor delegate = new TestExecutor();
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public String actionType() {
            return delegate.actionType();
        }

        @Override
        public GlobalActionResult execute(GlobalActionRequest request) {
            count.incrementAndGet();
            return delegate.execute(request);
        }

        int count() {
            return count.get();
        }
    }
}
