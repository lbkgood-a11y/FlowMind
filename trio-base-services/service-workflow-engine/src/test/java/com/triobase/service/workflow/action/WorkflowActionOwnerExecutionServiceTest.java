package com.triobase.service.workflow.action;

import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.AddSignRequest;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.service.workflow.dto.FormFieldValidationError;
import com.triobase.service.workflow.dto.ApproveTaskRequest;
import com.triobase.service.workflow.dto.ProcessClosureDetailResponse;
import com.triobase.service.workflow.dto.ProcessInstanceResponse;
import com.triobase.service.workflow.dto.RejectTaskRequest;
import com.triobase.service.workflow.dto.StartProcessRequest;
import com.triobase.service.workflow.dto.TaskResponse;
import com.triobase.service.workflow.dto.TransferTaskRequest;
import com.triobase.service.workflow.exception.FormDataValidationException;
import com.triobase.service.workflow.service.ClosureEffectOperationService;
import com.triobase.service.workflow.service.ProcessInstanceService;
import com.triobase.service.workflow.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowActionOwnerExecutionServiceTest {

    @Mock
    private ProcessInstanceService processInstanceService;
    @Mock
    private TaskService taskService;
    @Mock
    private ClosureEffectOperationService closureEffectOperationService;

    @InjectMocks
    private WorkflowActionOwnerExecutionService executionService;

    @Test
    void processStartMapsPayloadAndReturnsProcessInstanceData() {
        ProcessInstanceResponse process = new ProcessInstanceResponse();
        process.setId("PI001");
        process.setStatus("RUNNING");
        when(processInstanceService.startProcess(org.mockito.Mockito.any())).thenReturn(process);

        var response = executionService.execute(startRequest());

        assertEquals(ActionStatus.SUCCEEDED, response.getStatus());
        assertEquals("PI001", response.getOwnerExecutionRef());
        assertEquals(process, response.getData().get("processInstance"));
        ArgumentCaptor<StartProcessRequest> captor = ArgumentCaptor.forClass(StartProcessRequest.class);
        verify(processInstanceService).startProcess(captor.capture());
        assertEquals("expense_report", captor.getValue().getProcessKey());
        assertEquals("idem-001", captor.getValue().getIdempotencyKey());
        assertEquals(Map.of("amount", 12), captor.getValue().getFormData());
    }

    @Test
    void taskApproveUsesActionIdempotencyAsOperationId() {
        TaskResponse task = new TaskResponse();
        task.setId("TASK001");
        task.setStatus("APPROVED");
        when(taskService.approve(eq("TASK001"), org.mockito.Mockito.any())).thenReturn(task);

        var response = executionService.execute(taskRequest("process.task.approve"));

        assertEquals(ActionStatus.SUCCEEDED, response.getStatus());
        assertEquals("TASK001", response.getOwnerExecutionRef());
        assertEquals("APPROVED", response.getData().get("runtimeStatus"));
        ArgumentCaptor<ApproveTaskRequest> captor = ArgumentCaptor.forClass(ApproveTaskRequest.class);
        verify(taskService).approve(eq("TASK001"), captor.capture());
        assertEquals("idem-001", captor.getValue().getOperationId());
        assertEquals("APPROVE", captor.getValue().getAction());
    }

    @Test
    void taskApproveUsesActionIdWhenIdempotencyIsMissing() {
        TaskResponse task = new TaskResponse();
        task.setId("TASK001");
        task.setStatus("APPROVED");
        when(taskService.approve(eq("TASK001"), org.mockito.Mockito.any())).thenReturn(task);
        ActionOwnerDispatchRequest request = taskRequest("process.task.approve");
        request.setIdempotencyKey(null);

        executionService.execute(request);

        ArgumentCaptor<ApproveTaskRequest> captor = ArgumentCaptor.forClass(ApproveTaskRequest.class);
        verify(taskService).approve(eq("TASK001"), captor.capture());
        assertEquals("act_001", captor.getValue().getOperationId());
    }

    @Test
    void taskRejectTransferAndAddSignUseGlobalActionOperationIds() {
        TaskResponse rejected = task("TASK001", "REJECTED");
        when(taskService.reject(eq("TASK001"), any())).thenReturn(rejected);

        var rejectResponse = executionService.execute(taskRequest("process.task.reject",
                Map.of("taskId", "TASK001", "targetNodeId", "draft", "comment", "back")));

        assertEquals(ActionStatus.SUCCEEDED, rejectResponse.getStatus());
        assertEquals("REJECTED", rejectResponse.getData().get("runtimeStatus"));
        ArgumentCaptor<RejectTaskRequest> rejectCaptor = ArgumentCaptor.forClass(RejectTaskRequest.class);
        verify(taskService).reject(eq("TASK001"), rejectCaptor.capture());
        assertEquals("idem-001", rejectCaptor.getValue().getOperationId());
        assertEquals("draft", rejectCaptor.getValue().getTargetNodeId());

        TaskResponse transferred = task("TASK001", "TRANSFERRED");
        when(taskService.transfer(eq("TASK001"), any())).thenReturn(transferred);

        var transferResponse = executionService.execute(taskRequest("process.task.transfer",
                Map.of("taskId", "TASK001", "newAssigneeId", "U002", "newAssigneeName", "Bob")));

        assertEquals(ActionStatus.SUCCEEDED, transferResponse.getStatus());
        assertEquals("TRANSFERRED", transferResponse.getData().get("runtimeStatus"));
        ArgumentCaptor<TransferTaskRequest> transferCaptor = ArgumentCaptor.forClass(TransferTaskRequest.class);
        verify(taskService).transfer(eq("TASK001"), transferCaptor.capture());
        assertEquals("idem-001", transferCaptor.getValue().getOperationId());
        assertEquals("U002", transferCaptor.getValue().getNewAssigneeId());

        TaskResponse added = task("TASK001", "PENDING");
        when(taskService.addSign(eq("TASK001"), any())).thenReturn(added);

        var addSignResponse = executionService.execute(taskRequest("process.task.addSign",
                Map.of("taskId", "TASK001", "assigneeId", "U003", "assigneeName", "Carol")));

        assertEquals(ActionStatus.SUCCEEDED, addSignResponse.getStatus());
        assertEquals("PENDING", addSignResponse.getData().get("runtimeStatus"));
        ArgumentCaptor<AddSignRequest> addSignCaptor = ArgumentCaptor.forClass(AddSignRequest.class);
        verify(taskService).addSign(eq("TASK001"), addSignCaptor.capture());
        assertEquals("idem-001", addSignCaptor.getValue().getOperationId());
        assertEquals("U003", addSignCaptor.getValue().getAssigneeId());
    }

    @Test
    void closureMarkHandledReturnsEffectData() {
        ProcessClosureDetailResponse.EffectItem effect = new ProcessClosureDetailResponse.EffectItem();
        effect.setId("EFF001");
        effect.setStatus("MANUALLY_HANDLED");
        when(closureEffectOperationService.markHandled("EFF001", "handled offline")).thenReturn(effect);

        var response = executionService.execute(closureRequest("process.closure.effect.markHandled"));

        assertEquals(ActionStatus.SUCCEEDED, response.getStatus());
        assertEquals("EFF001", response.getOwnerExecutionRef());
        assertEquals("MANUALLY_HANDLED", response.getData().get("runtimeStatus"));
    }

    @Test
    void closureRetryReturnsEffectData() {
        ProcessClosureDetailResponse.EffectItem effect = new ProcessClosureDetailResponse.EffectItem();
        effect.setId("EFF001");
        effect.setStatus("RETRYING");
        when(closureEffectOperationService.retry("EFF001")).thenReturn(effect);

        var response = executionService.execute(closureRequest("process.closure.effect.retry"));

        assertEquals(ActionStatus.SUCCEEDED, response.getStatus());
        assertEquals("EFF001", response.getOwnerExecutionRef());
        assertEquals("RETRYING", response.getData().get("runtimeStatus"));
    }

    @Test
    void guardDeniedBusinessFailureRejectsWithoutRuntimeSuccess() {
        when(taskService.approve(eq("TASK001"), any()))
                .thenThrow(new BizException(40300, "TASK_GUARD_DENIED"));

        var response = executionService.execute(taskRequest("process.task.approve"));

        assertEquals(ActionStatus.REJECTED, response.getStatus());
        assertEquals("TASK_GUARD_DENIED", response.getMessage());
        assertEquals(ActionErrorCategory.AUTHORIZATION, response.getErrors().getFirst().getCategory());
    }

    @Test
    void invalidProcessPayloadRejectsWithStructuredFieldErrors() {
        when(processInstanceService.startProcess(any()))
                .thenThrow(new FormDataValidationException(
                        java.util.List.of(new FormFieldValidationError(
                                "amount", "required", "amount required", "required"))));

        var response = executionService.execute(startRequest());

        assertEquals(ActionStatus.REJECTED, response.getStatus());
        assertEquals("FORM_DATA_VALIDATION_FAILED", response.getErrors().getFirst().getCode());
        assertEquals(ActionErrorCategory.VALIDATION, response.getErrors().getFirst().getCategory());
        assertEquals(1, ((java.util.List<?>) response.getData().get("fieldErrors")).size());
    }

    @Test
    void unsupportedActionFailsClosed() {
        var response = executionService.execute(taskRequest("process.task.archive"));

        assertEquals(ActionStatus.REJECTED, response.getStatus());
        assertEquals("WORKFLOW_ACTION_UNSUPPORTED", response.getMessage());
    }

    private ActionOwnerDispatchRequest startRequest() {
        ActionOwnerDispatchRequest request = base("process.instance.start");
        request.setTarget(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("processKey", "expense_report");
        payload.put("title", "Expense");
        payload.put("formData", Map.of("amount", 12));
        request.setPayload(payload);
        return request;
    }

    private ActionOwnerDispatchRequest taskRequest(String actionType) {
        return taskRequest(actionType, Map.of("taskId", "TASK001", "comment", "ok"));
    }

    private ActionOwnerDispatchRequest taskRequest(String actionType, Map<String, Object> payload) {
        ActionOwnerDispatchRequest request = base(actionType);
        request.setPayload(payload);
        return request;
    }

    private ActionOwnerDispatchRequest closureRequest(String actionType) {
        ActionOwnerDispatchRequest request = base(actionType);
        request.setPayload(Map.of("effectId", "EFF001", "reason", "handled offline"));
        return request;
    }

    private ActionOwnerDispatchRequest base(String actionType) {
        ActionOwnerDispatchRequest request = new ActionOwnerDispatchRequest();
        request.setActionId("act_001");
        request.setActionType(actionType);
        request.setOwnerService("service-workflow-engine");
        request.setSource(ActionSource.GUI);
        request.setExecutionMode(ActionExecutionMode.SYNC);
        request.setIdempotencyKey("idem-001");
        return request;
    }

    private TaskResponse task(String id, String status) {
        TaskResponse task = new TaskResponse();
        task.setId(id);
        task.setStatus(status);
        return task;
    }
}
