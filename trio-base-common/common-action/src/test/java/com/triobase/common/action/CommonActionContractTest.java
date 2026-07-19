package com.triobase.common.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.definition.ActionGuardRequirement;
import com.triobase.common.action.definition.ActionSensitivePath;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionAuditLevel;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionAvailability;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.util.ActionCorrelationIds;
import com.triobase.common.action.util.ActionIdempotencyKeys;
import com.triobase.common.action.util.ActionPayloadRedactor;
import com.triobase.common.action.util.ActionTypeValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonActionContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesAndDeserializesGlobalActionRequest() throws Exception {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionId("act_test");
        request.setActionType("process.task.approve");
        request.setSource(ActionSource.GUI);
        request.setExecutionMode(ActionExecutionMode.SIGNAL);
        request.setIdempotencyKey("idem-1");
        request.getActor().setType(ActionActorType.USER);
        request.getActor().setId("user-1");
        request.getTarget().setType("workflow-task");
        request.getTarget().setId("task-1");
        request.getPayload().put("comment", "ok");

        String json = objectMapper.writeValueAsString(request);
        GlobalActionRequest restored = objectMapper.readValue(json, GlobalActionRequest.class);

        assertThat(restored.getActionType()).isEqualTo("process.task.approve");
        assertThat(restored.getSource()).isEqualTo(ActionSource.GUI);
        assertThat(restored.getActor().getId()).isEqualTo("user-1");
        assertThat(restored.getPayload()).containsEntry("comment", "ok");
    }

    @Test
    void serializesActionDefinitionWithGuardsAndSensitivePaths() throws Exception {
        ActionDefinition definition = new ActionDefinition();
        definition.setActionType("lowcode.form.submit");
        definition.setOwnerService("service-lowcode");
        definition.setTargetType("lowcode-form");
        definition.setRequiredPermission("LOWCODE_FORM:SUBMIT");
        definition.setExecutionMode(ActionExecutionMode.ASYNC);
        definition.setAuditLevel(ActionAuditLevel.SENSITIVE);
        ActionGuardRequirement guard = new ActionGuardRequirement();
        guard.setGuardCode("FORM_PUBLISHED");
        definition.getRequiredGuards().add(guard);
        definition.getSensitivePayloadPaths().add(ActionSensitivePath.of("bank.cardNo"));

        String json = objectMapper.writeValueAsString(definition);
        ActionDefinition restored = objectMapper.readValue(json, ActionDefinition.class);

        assertThat(restored.getRequiredGuards()).hasSize(1);
        assertThat(restored.getSensitivePayloadPaths().getFirst().getPath()).isEqualTo("bank.cardNo");
        assertThat(restored.getAuditLevel()).isEqualTo(ActionAuditLevel.SENSITIVE);
    }

    @Test
    void identifiesTerminalStatuses() {
        assertThat(ActionStatus.SUCCEEDED.terminal()).isTrue();
        assertThat(ActionStatus.REJECTED.terminal()).isTrue();
        assertThat(ActionStatus.RUNNING.terminal()).isFalse();
    }

    @Test
    void globalActionResultReportsTerminalState() {
        GlobalActionResult result = new GlobalActionResult();
        result.setActionId("act_1");
        result.setActionType("process.task.approve");
        result.setStatus(ActionStatus.FAILED);
        result.setTargetStatus("REJECTED");
        result.setTargetStatusGroup("TERMINAL");
        result.getRefreshScopes().add("timeline");
        result.getOwnerExecutionMetadata().put("workflowId", "wf-1");
        result.getErrors().add(ActionError.of("TASK_CLOSED", ActionErrorCategory.CONFLICT, "Task is closed"));

        assertThat(result.terminal()).isTrue();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getRefreshScopes()).contains("timeline");
        assertThat(result.getOwnerExecutionMetadata()).containsEntry("workflowId", "wf-1");
    }

    @Test
    void serializesActionAvailabilityMetadata() throws Exception {
        ActionAvailability availability = ActionAvailability.disabled(
                "scm.purchaseOrder.submit",
                "DOCUMENT_ALREADY_SUBMITTED");
        availability.setDanger(true);
        availability.setRequiresConfirmation(true);
        availability.setExecutionMode(ActionExecutionMode.WORKFLOW);
        availability.setTargetStatus("SUBMITTED");
        availability.setTargetStatusGroup("PENDING");
        availability.getRefreshScopes().addAll(List.of("document", "actions", "timeline"));

        String json = objectMapper.writeValueAsString(availability);
        ActionAvailability restored = objectMapper.readValue(json, ActionAvailability.class);

        assertThat(restored.isVisible()).isTrue();
        assertThat(restored.isEnabled()).isFalse();
        assertThat(restored.getDisabledReason()).isEqualTo("DOCUMENT_ALREADY_SUBMITTED");
        assertThat(restored.getExecutionMode()).isEqualTo(ActionExecutionMode.WORKFLOW);
        assertThat(restored.getRefreshScopes()).containsExactly("document", "actions", "timeline");
    }

    @Test
    void validatesNamespacedActionType() {
        assertThat(ActionTypeValidator.valid("process.task.approve")).isTrue();
        assertThat(ActionTypeValidator.valid("process.task.addSign")).isTrue();
        assertThat(ActionTypeValidator.valid("approve")).isFalse();
        assertThatThrownBy(() -> ActionTypeValidator.requireValid("bad action"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesAndScopesIdempotencyKey() {
        assertThat(ActionIdempotencyKeys.normalize(" idem-1 ")).isEqualTo("idem-1");
        assertThat(ActionIdempotencyKeys.scoped("tenant-a", "process.task.approve", "idem-1"))
                .isEqualTo("tenant-a:process.task.approve:idem-1");
        assertThatThrownBy(() -> ActionIdempotencyKeys.require(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatesActionAndCorrelationIds() {
        assertThat(ActionCorrelationIds.newActionId()).startsWith("act_");
        assertThat(ActionCorrelationIds.newCorrelationId()).startsWith("corr_");
        assertThat(ActionCorrelationIds.newRequestId()).startsWith("req_");
        assertThat(ActionCorrelationIds.firstNonBlank(" trace ", "fallback")).isEqualTo("trace");
    }

    @Test
    void redactsConfiguredSensitivePathsWithoutMutatingOriginal() {
        Map<String, Object> payload = Map.of(
                "amount", 100,
                "bank", Map.of("cardNo", "62220000", "name", "Ada"));

        Map<String, Object> redacted = ActionPayloadRedactor.redact(
                payload, List.of(ActionSensitivePath.of("bank.cardNo")));

        Map<?, ?> redactedBank = (Map<?, ?>) redacted.get("bank");
        Map<?, ?> originalBank = (Map<?, ?>) payload.get("bank");
        assertThat(redactedBank.get("cardNo")).isEqualTo(ActionPayloadRedactor.REDACTED);
        assertThat(redactedBank.get("name")).isEqualTo("Ada");
        assertThat(originalBank.get("cardNo")).isEqualTo("62220000");
    }

    @Test
    void boundsSummaryLength() {
        assertThat(ActionPayloadRedactor.boundedSummary("abcdef", 3)).isEqualTo("abc");
        assertThat(ActionPayloadRedactor.boundedSummary(null)).isNull();
    }
}
