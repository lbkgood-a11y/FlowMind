package com.triobase.service.openapi.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.OrchestrationExecutionResponse;
import com.triobase.service.openapi.dto.RuntimeAdmissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenApiActionDispatchServiceTest {

    private final OpenApiGlobalActionClient actionClient = mock(OpenApiGlobalActionClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenApiActionDispatchService service =
            new OpenApiActionDispatchService(actionClient, objectMapper);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
        TraceUtil.clear();
    }

    @Test
    void orchestrationStartSubmitsEquivalentGlobalAction() {
        TraceUtil.setTraceId("trace-openapi-1");
        OrchestrationExecutionResponse orchestration = new OrchestrationExecutionResponse(
                "EXEC001", "wf-1", "run-1", ExecutionState.RUNNING, false,
                "trace-openapi-1", objectMapper.createObjectNode().put("accepted", true));
        GlobalActionResult actionResult = new GlobalActionResult();
        actionResult.setStatus(ActionStatus.SUCCEEDED);
        actionResult.getData().put("orchestration", orchestration);
        when(actionClient.submit(any())).thenReturn(actionResult);

        var response = service.startOrchestration(
                "orders.submit",
                Environment.PROD,
                new RuntimeAdmissionContext("tenant-a", Environment.PROD,
                        "client-a", "sub-a", 7L, 3L, 10L),
                "POST",
                "idem-1",
                objectMapper.createObjectNode().put("amount", 12));

        assertThat(response.executionId()).isEqualTo("EXEC001");
        ArgumentCaptor<GlobalActionRequest> captor = ArgumentCaptor.forClass(GlobalActionRequest.class);
        verify(actionClient).submit(captor.capture());
        GlobalActionRequest action = captor.getValue();
        assertThat(action.getActionType()).isEqualTo("integration.orchestration.start");
        assertThat(action.getSource()).isEqualTo(ActionSource.API);
        assertThat(action.getExecutionMode()).isEqualTo(ActionExecutionMode.WORKFLOW);
        assertThat(action.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(action.getActor().getType()).isEqualTo(ActionActorType.SERVICE);
        assertThat(action.getActor().getId()).isEqualTo("client-a");
        assertThat(action.getTarget().getType()).isEqualTo("INTEGRATION_ROUTE");
        assertThat(action.getTarget().getId()).isEqualTo("orders.submit");
        assertThat(action.getContext().getTenantId()).isEqualTo("tenant-a");
        assertThat(action.getContext().getTraceId()).isEqualTo("trace-openapi-1");
        assertThat(action.getPayload()).containsEntry("routeKey", "orders.submit")
                .containsEntry("environment", "PROD")
                .containsEntry("operation", "POST")
                .containsEntry("idempotencyKey", "idem-1");
    }

    @Test
    void orchestrationCancelSubmitsEquivalentGlobalAction() {
        TraceUtil.setTraceId("trace-openapi-cancel");
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "APP:client-a", "client-a", "tenant-a", List.of("EXTERNAL_APPLICATION"),
                List.of(), 1L, 1L, 1L));
        OrchestrationExecutionResponse orchestration = new OrchestrationExecutionResponse(
                "EXEC001", "wf-1", "run-1", ExecutionState.RUNNING, true,
                "trace-openapi-cancel", objectMapper.createObjectNode().put("cancelRequested", true));
        GlobalActionResult actionResult = new GlobalActionResult();
        actionResult.setStatus(ActionStatus.SUCCEEDED);
        actionResult.getData().put("orchestration", orchestration);
        when(actionClient.submit(any())).thenReturn(actionResult);

        var response = service.cancelOrchestration(
                "EXEC001", "client-a", null, "operator requested");

        assertThat(response.executionId()).isEqualTo("EXEC001");
        ArgumentCaptor<GlobalActionRequest> captor = ArgumentCaptor.forClass(GlobalActionRequest.class);
        verify(actionClient).submit(captor.capture());
        GlobalActionRequest action = captor.getValue();
        assertThat(action.getActionType()).isEqualTo("integration.orchestration.cancel");
        assertThat(action.getSource()).isEqualTo(ActionSource.API);
        assertThat(action.getExecutionMode()).isEqualTo(ActionExecutionMode.SIGNAL);
        assertThat(action.getIdempotencyKey()).startsWith("cancel:EXEC001:");
        assertThat(action.getActor().getType()).isEqualTo(ActionActorType.SERVICE);
        assertThat(action.getActor().getId()).isEqualTo("client-a");
        assertThat(action.getTarget().getType()).isEqualTo("INTEGRATION_EXECUTION");
        assertThat(action.getTarget().getId()).isEqualTo("EXEC001");
        assertThat(action.getContext().getTenantId()).isEqualTo("tenant-a");
        assertThat(action.getContext().getTraceId()).isEqualTo("trace-openapi-cancel");
        assertThat(action.getPayload()).containsEntry("executionId", "EXEC001")
                .containsEntry("applicationClientId", "client-a")
                .containsEntry("reason", "operator requested");
    }
}
