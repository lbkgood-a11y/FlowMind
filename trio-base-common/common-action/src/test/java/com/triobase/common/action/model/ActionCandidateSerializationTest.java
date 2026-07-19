package com.triobase.common.action.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.owner.ActionOwnerAdapterSupport;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.action.owner.ActionOwnerDispatchResponse;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionCandidateSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesCandidateAndConvertsToActionRequest() throws Exception {
        ActionCandidate candidate = new ActionCandidate();
        candidate.setCandidateId("cand-1");
        candidate.setActionType("process.task.approve");
        candidate.setSource(ActionSource.LUI);
        candidate.setIdempotencyKey("idem-1");
        candidate.getActor().setType(ActionActorType.USER);
        candidate.getActor().setId("user-1");
        candidate.getTarget().setType("PROCESS_TASK");
        candidate.getTarget().setId("task-1");
        candidate.setPayload(Map.of("taskId", "task-1"));

        String json = objectMapper.writeValueAsString(candidate);
        ActionCandidate restored = objectMapper.readValue(json, ActionCandidate.class);
        GlobalActionRequest request = restored.toActionRequest();

        assertThat(restored.getSource()).isEqualTo(ActionSource.LUI);
        assertThat(request.getActionType()).isEqualTo("process.task.approve");
        assertThat(request.getPayload()).containsEntry("taskId", "task-1");
        assertThat(request.getActor().getId()).isEqualTo("user-1");
    }

    @Test
    void mapsStructuredOwnerGuardDenialToActionResponse() {
        ActionOwnerDispatchRequest request = new ActionOwnerDispatchRequest();
        request.setActionId("act-1");
        request.setOwnerService("service-workflow-engine");
        ActionOwnerGuardResponse guard = ActionOwnerGuardResponse.denied(
                "NO_SELF_APPROVAL",
                "submitter cannot approve",
                List.of(ActionError.of("NO_SELF_APPROVAL", ActionErrorCategory.GUARD,
                        "submitter cannot approve")));

        ActionOwnerDispatchResponse response = new ActionOwnerAdapterSupport()
                .guardDenied(request, guard);

        assertThat(response.getStatus()).isEqualTo(ActionStatus.REJECTED);
        assertThat(response.getErrors()).extracting(ActionError::getCode)
                .contains("NO_SELF_APPROVAL");
        assertThat(response.getData()).containsKey("guard");
    }

    @Test
    void mapsOwnerRefreshAndExecutionMetadataToGlobalResult() {
        ActionOwnerDispatchRequest request = new ActionOwnerDispatchRequest();
        request.setActionId("act-2");
        request.setOwnerService("service-scm");
        ActionOwnerDispatchResponse response = new ActionOwnerAdapterSupport()
                .accepted(request, "wf-po-1", Map.of("documentId", "PO-1"));
        response.setTargetStatus("APPROVING");
        response.setTargetStatusGroup("PENDING");
        response.getRefreshScopes().addAll(List.of("document", "actions", "timeline"));
        response.getOwnerExecutionMetadata().put("workflowId", "wf-po-1");

        GlobalActionResult result = new ActionOwnerAdapterSupport().toGlobalResult(response);

        assertThat(result.getOwnerExecutionRef()).isEqualTo("wf-po-1");
        assertThat(result.getTargetStatus()).isEqualTo("APPROVING");
        assertThat(result.getRefreshScopes()).containsExactly("document", "actions", "timeline");
        assertThat(result.getOwnerExecutionMetadata()).containsEntry("workflowId", "wf-po-1");
    }
}
