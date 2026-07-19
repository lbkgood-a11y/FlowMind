package com.triobase.service.lowcode.action;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.service.ApplicationRuntimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LowcodeActionOwnerExecutionServiceTest {

    @Mock
    private ApplicationRuntimeService applicationRuntimeService;

    @InjectMocks
    private LowcodeActionOwnerExecutionService executionService;

    @Test
    void formSubmitDelegatesToLocalRuntimeAndReturnsSucceededActionResult() {
        GlobalActionResult runtimeResponse = result(ActionStatus.SUCCEEDED, "INS001",
                Map.of("actionCode", "save",
                        "runtimeStatus", "FORM_SAVED",
                        "formInstance", instance("INS001", "SUBMITTED")));
        when(applicationRuntimeService.executeLocalAction(eq("expense_report"), eq(1), eq("save"), any()))
                .thenReturn(runtimeResponse);

        var response = executionService.execute(request("lowcode.form.submit", "save"));

        assertEquals(ActionStatus.SUCCEEDED, response.getStatus());
        assertEquals("INS001", response.getOwnerExecutionRef());
        assertEquals("FORM_SAVED", response.getData().get("runtimeStatus"));
        ArgumentCaptor<GlobalActionRequest> captor = ArgumentCaptor.forClass(GlobalActionRequest.class);
        verify(applicationRuntimeService).executeLocalAction(eq("expense_report"), eq(1), eq("save"), captor.capture());
        assertEquals(Map.of("amount", 12), captor.getValue().getPayload().get("data"));
        assertEquals("idem-001", captor.getValue().getIdempotencyKey());
    }

    @Test
    void workflowPendingMapsToFailedRetryableActionWhilePreservingRuntimeDetail() {
        GlobalActionResult runtimeResponse = result(ActionStatus.FAILED, "INS001",
                Map.of("actionCode", "submit",
                        "runtimeStatus", "WORKFLOW_PENDING",
                        "formInstance", instance("INS001", "PENDING_WORKFLOW")));
        runtimeResponse.setRetryable(true);
        runtimeResponse.setMessage("downstream unavailable");
        runtimeResponse.setErrors(List.of(ActionError.of(
                "WORKFLOW_START_UNAVAILABLE",
                ActionErrorCategory.EXECUTION,
                "downstream unavailable")));
        when(applicationRuntimeService.executeLocalAction(eq("expense_report"), eq(1), eq("submit"), any()))
                .thenReturn(runtimeResponse);

        var response = executionService.execute(request("lowcode.form.submit", "submit"));

        assertEquals(ActionStatus.FAILED, response.getStatus());
        assertThat(response.isRetryable()).isTrue();
        assertEquals("downstream unavailable", response.getMessage());
        assertEquals("WORKFLOW_PENDING", response.getData().get("runtimeStatus"));
        assertEquals("INS001", response.getOwnerExecutionRef());
        assertThat(response.getErrors()).hasSize(1);
        assertEquals(ActionErrorCategory.EXECUTION, response.getErrors().getFirst().getCategory());
        assertEquals("WORKFLOW_START_UNAVAILABLE", response.getErrors().getFirst().getCode());
    }

    @Test
    void unsupportedActionFailsClosed() {
        var response = executionService.execute(request("lowcode.form.delete", "delete"));

        assertEquals(ActionStatus.REJECTED, response.getStatus());
        assertEquals("LOWCODE_ACTION_UNSUPPORTED", response.getMessage());
    }

    private ActionOwnerDispatchRequest request(String actionType, String actionCode) {
        ActionOwnerDispatchRequest request = new ActionOwnerDispatchRequest();
        request.setActionId("act_001");
        request.setActionType(actionType);
        request.setOwnerService("service-lowcode");
        request.setSource(ActionSource.GUI);
        request.setExecutionMode(ActionExecutionMode.SYNC);
        request.setIdempotencyKey("idem-001");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appKey", "expense_report");
        payload.put("version", 1);
        payload.put("actionCode", actionCode);
        payload.put("data", Map.of("amount", 12));
        request.setPayload(payload);
        return request;
    }

    private GlobalActionResult result(ActionStatus status,
                                      String ownerExecutionRef,
                                      Map<String, Object> data) {
        GlobalActionResult result = new GlobalActionResult();
        result.setStatus(status);
        result.setOwnerExecutionRef(ownerExecutionRef);
        result.setData(data);
        return result;
    }

    private FormInstanceResponse instance(String id, String workflowStatus) {
        FormInstanceResponse response = new FormInstanceResponse();
        response.setId(id);
        response.setWorkflowStatus(workflowStatus);
        return response;
    }
}
