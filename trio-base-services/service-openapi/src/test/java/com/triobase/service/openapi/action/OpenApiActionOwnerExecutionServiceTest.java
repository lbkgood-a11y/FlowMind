package com.triobase.service.openapi.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.enums.CallbackInboxState;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.OrchestrationExecutionResponse;
import com.triobase.service.openapi.dto.RuntimeAdmissionContext;
import com.triobase.service.openapi.service.CallbackSignalDispatcher;
import com.triobase.service.openapi.service.OrchestrationRuntimeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenApiActionOwnerExecutionServiceTest {

    private final OrchestrationRuntimeService orchestrationRuntimeService = mock(OrchestrationRuntimeService.class);
    private final CallbackSignalDispatcher callbackSignalDispatcher = mock(CallbackSignalDispatcher.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenApiActionOwnerExecutionService service = new OpenApiActionOwnerExecutionService(
            orchestrationRuntimeService, callbackSignalDispatcher, objectMapper);

    @Test
    void orchestrationStartRestoresRuntimeRequest() {
        OrchestrationExecutionResponse orchestration = new OrchestrationExecutionResponse(
                "EXEC001", "wf-1", "run-1", ExecutionState.RUNNING,
                false, "trace-1", objectMapper.createObjectNode().put("accepted", true));
        when(orchestrationRuntimeService.start(
                eq("orders.submit"),
                eq(Environment.PROD),
                org.mockito.Mockito.any(),
                eq("POST"),
                eq("idem-1"),
                org.mockito.Mockito.any())).thenReturn(orchestration);

        var response = service.execute(orchestrationStartRequest());

        assertThat(response.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(response.getOwnerExecutionRef()).isEqualTo("EXEC001");
        assertThat(response.getData().get("runtimeStatus")).isEqualTo("RUNNING");
        ArgumentCaptor<RuntimeAdmissionContext> admission =
                ArgumentCaptor.forClass(RuntimeAdmissionContext.class);
        verify(orchestrationRuntimeService).start(
                eq("orders.submit"),
                eq(Environment.PROD),
                admission.capture(),
                eq("POST"),
                eq("idem-1"),
                org.mockito.Mockito.any());
        assertThat(admission.getValue().applicationClientId()).isEqualTo("client-a");
        assertThat(admission.getValue().maxActiveWorkflows()).isEqualTo(10L);
    }

    @Test
    void callbackSignalDeferredReturnsRetryableFailure() {
        CallbackInbox inbox = new CallbackInbox();
        inbox.setId("INBOX001");
        inbox.setInboxState(CallbackInboxState.SIGNAL_PENDING);
        inbox.setLastSignalError("CALLBACK_SIGNAL_TEMPORARILY_UNAVAILABLE");
        when(callbackSignalDispatcher.dispatchInbox("INBOX001")).thenReturn(inbox);

        var response = service.execute(callbackSignalRequest());

        assertThat(response.getStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(response.isRetryable()).isTrue();
        assertThat(response.getOwnerExecutionRef()).isEqualTo("INBOX001");
        assertThat(response.getErrors()).extracting("code")
                .containsExactly("OPENAPI_CALLBACK_SIGNAL_DEFERRED");
    }

    @Test
    void orchestrationCancelUsesGlobalActionOwnerExecution() {
        OrchestrationExecutionResponse orchestration = new OrchestrationExecutionResponse(
                "EXEC001", "wf-1", "run-1", ExecutionState.RUNNING,
                true, "trace-1", objectMapper.createObjectNode().put("cancelRequested", true));
        when(orchestrationRuntimeService.cancel(
                eq("EXEC001"),
                eq("client-a"),
                eq("operator requested"))).thenReturn(orchestration);

        var response = service.execute(orchestrationCancelRequest());

        assertThat(response.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(response.getOwnerExecutionRef()).isEqualTo("EXEC001");
        assertThat(response.getData().get("runtimeStatus")).isEqualTo("RUNNING");
        verify(orchestrationRuntimeService).cancel(
                eq("EXEC001"),
                eq("client-a"),
                eq("operator requested"));
    }

    @Test
    void capacityRejectionFromRuntimeMapsToRejectedActionFailure() {
        when(orchestrationRuntimeService.start(
                eq("orders.submit"),
                eq(Environment.PROD),
                org.mockito.Mockito.any(),
                eq("POST"),
                eq("idem-1"),
                org.mockito.Mockito.any()))
                .thenThrow(new BizException(42960, "OPENAPI_ASYNC_CAPACITY_EXHAUSTED"));

        var response = service.execute(orchestrationStartRequest());

        assertThat(response.getStatus()).isEqualTo(ActionStatus.REJECTED);
        assertThat(response.isRetryable()).isFalse();
        assertThat(response.getMessage()).isEqualTo("OPENAPI_ASYNC_CAPACITY_EXHAUSTED");
        assertThat(response.getErrors()).extracting("category")
                .containsExactly(ActionErrorCategory.VALIDATION);
    }

    @Test
    void stateChangingInvocationIsFailClosedUntilDedicatedOwnerSupportExists() {
        var response = service.execute(base("integration.invocation.stateChanging"));

        assertThat(response.getStatus()).isEqualTo(ActionStatus.REJECTED);
        assertThat(response.getErrors()).extracting("code")
                .containsExactly("OPENAPI_STATE_CHANGING_SYNC_ACTION_NOT_SUPPORTED");
    }

    private ActionOwnerDispatchRequest orchestrationStartRequest() {
        ActionOwnerDispatchRequest request = base("integration.orchestration.start");
        Map<String, Object> admission = new LinkedHashMap<>();
        admission.put("tenantId", "tenant-a");
        admission.put("environment", "PROD");
        admission.put("applicationClientId", "client-a");
        admission.put("subscriptionId", "sub-a");
        admission.put("policyVersion", 7L);
        admission.put("maxConcurrency", 3L);
        admission.put("maxActiveWorkflows", 10L);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("routeKey", "orders.submit");
        payload.put("environment", "PROD");
        payload.put("operation", "POST");
        payload.put("idempotencyKey", "idem-1");
        payload.put("admission", admission);
        payload.put("payload", Map.of("amount", 12));
        request.setPayload(payload);
        request.setIdempotencyKey("idem-1");
        return request;
    }

    private ActionOwnerDispatchRequest orchestrationCancelRequest() {
        ActionOwnerDispatchRequest request = base("integration.orchestration.cancel");
        request.setExecutionMode(ActionExecutionMode.SIGNAL);
        request.setPayload(Map.of(
                "executionId", "EXEC001",
                "applicationClientId", "client-a",
                "reason", "operator requested"));
        request.setIdempotencyKey("cancel-EXEC001");
        return request;
    }

    private ActionOwnerDispatchRequest callbackSignalRequest() {
        ActionOwnerDispatchRequest request = base("integration.callback.signal");
        request.setPayload(Map.of("inboxId", "INBOX001"));
        return request;
    }

    private ActionOwnerDispatchRequest base(String actionType) {
        ActionOwnerDispatchRequest request = new ActionOwnerDispatchRequest();
        request.setActionId("act_openapi_001");
        request.setActionType(actionType);
        request.setOwnerService("service-openapi");
        request.setSource(ActionSource.API);
        request.setExecutionMode(ActionExecutionMode.SYNC);
        return request;
    }
}
