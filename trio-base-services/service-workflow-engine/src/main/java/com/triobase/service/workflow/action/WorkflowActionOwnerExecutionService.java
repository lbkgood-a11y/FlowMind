package com.triobase.service.workflow.action;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerExecutor;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.action.runtime.ActionStatusMachine;
import com.triobase.common.action.util.ActionHelpers;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.AddSignRequest;
import com.triobase.service.workflow.dto.ApproveTaskRequest;
import com.triobase.service.workflow.dto.ProcessClosureDetailResponse;
import com.triobase.service.workflow.dto.ProcessInstanceResponse;
import com.triobase.service.workflow.dto.RejectTaskRequest;
import com.triobase.service.workflow.dto.StartProcessRequest;
import com.triobase.service.workflow.dto.TaskResponse;
import com.triobase.service.workflow.dto.TransferTaskRequest;
import com.triobase.common.dto.form.FormDataValidationException;
import com.triobase.service.workflow.exception.ProcessVersionConflictException;
import com.triobase.service.workflow.service.ClosureEffectOperationService;
import com.triobase.service.workflow.service.ProcessInstanceService;
import com.triobase.service.workflow.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowActionOwnerExecutionService implements ActionOwnerExecutor {

    private static final Set<String> TERMINAL_STATUSES =
            Set.of("COMPLETED", "APPROVED", "REJECTED", "HANDLED");

    private static final String PROCESS_INSTANCE_START = "process.instance.start";
    private static final String TASK_APPROVE = "process.task.approve";
    private static final String TASK_REJECT = "process.task.reject";
    private static final String TASK_TRANSFER = "process.task.transfer";
    private static final String TASK_ADD_SIGN = "process.task.addSign";
    private static final String CLOSURE_RETRY = "process.closure.effect.retry";
    private static final String CLOSURE_MARK_HANDLED = "process.closure.effect.markHandled";

    private final ProcessInstanceService processInstanceService;
    private final TaskService taskService;
    private final ClosureEffectOperationService closureEffectOperationService;

    @Override
    public String actionType() {
        return "process.*";
    }

    @Override
    public GlobalActionResult execute(GlobalActionRequest request) {
        try {
            return switch (request.getActionType()) {
                case PROCESS_INSTANCE_START -> startProcess(request);
                case TASK_APPROVE -> approveTask(request);
                case TASK_REJECT -> rejectTask(request);
                case TASK_TRANSFER -> transferTask(request);
                case TASK_ADD_SIGN -> addSignTask(request);
                case CLOSURE_RETRY -> retryClosureEffect(request);
                case CLOSURE_MARK_HANDLED -> markClosureEffectHandled(request);
                default -> unsupported(request);
            };
        } catch (FormDataValidationException exception) {
            return formValidationFailure(request, exception);
        } catch (ProcessVersionConflictException exception) {
            return versionConflictFailure(request, exception);
        } catch (BizException exception) {
            return businessFailure(request, exception);
        }
    }

    public ActionOwnerGuardResponse guard(GlobalActionRequest request) {
        if (request == null || !supported(request.getActionType())) {
            return ActionOwnerGuardResponse.denied(
                    "WORKFLOW_ACTION_UNSUPPORTED",
                    "WORKFLOW_ACTION_UNSUPPORTED",
                    List.of(ActionError.of(
                            "WORKFLOW_ACTION_UNSUPPORTED",
                            ActionErrorCategory.VALIDATION,
                            "WORKFLOW_ACTION_UNSUPPORTED")));
        }
        return ActionOwnerGuardResponse.allowed("WORKFLOW_ACTION_SUPPORTED");
    }

    private GlobalActionResult startProcess(GlobalActionRequest request) {
        StartProcessRequest startRequest = new StartProcessRequest();
        startRequest.setProcessPackageId(request.string("processPackageId"));
        startRequest.setVersion(request.integer("version"));
        startRequest.setProcessKey(request.string("processKey"));
        startRequest.setTitle(request.string("title"));
        startRequest.setFormData(map(request.getPayload().get("formData")));
        startRequest.setLaunchMode(request.string("launchMode"));
        startRequest.setBusinessType(request.string("businessType"));
        startRequest.setBusinessId(request.string("businessId"));
        startRequest.setIdempotencyKey(ActionHelpers.firstNonBlank(
                request.string("idempotencyKey"), request.getIdempotencyKey()));
        ProcessInstanceResponse processInstance = processInstanceService.startProcess(startRequest);
        return success(request, processInstance.getId(), Map.of(
                "runtimeStatus", processInstance.getStatus(),
                "processInstance", processInstance));
    }

    private GlobalActionResult approveTask(GlobalActionRequest request) {
        ApproveTaskRequest approveRequest = new ApproveTaskRequest();
        approveRequest.setOperationId(operationId(request));
        approveRequest.setAction(ActionHelpers.firstNonBlank(request.string("action"), "APPROVE"));
        approveRequest.setComment(request.string("comment"));
        TaskResponse task = taskService.approve(taskId(request), approveRequest);
        return taskSuccess(request, task);
    }

    private GlobalActionResult rejectTask(GlobalActionRequest request) {
        RejectTaskRequest rejectRequest = new RejectTaskRequest();
        rejectRequest.setOperationId(operationId(request));
        rejectRequest.setTargetNodeId(request.string("targetNodeId"));
        rejectRequest.setComment(request.string("comment"));
        TaskResponse task = taskService.reject(taskId(request), rejectRequest);
        return taskSuccess(request, task);
    }

    private GlobalActionResult transferTask(GlobalActionRequest request) {
        TransferTaskRequest transferRequest = new TransferTaskRequest();
        transferRequest.setOperationId(operationId(request));
        transferRequest.setNewAssigneeId(request.string("newAssigneeId"));
        transferRequest.setNewAssigneeName(request.string("newAssigneeName"));
        TaskResponse task = taskService.transfer(taskId(request), transferRequest);
        return taskSuccess(request, task);
    }

    private GlobalActionResult addSignTask(GlobalActionRequest request) {
        AddSignRequest addSignRequest = new AddSignRequest();
        addSignRequest.setOperationId(operationId(request));
        addSignRequest.setAssigneeId(request.string("assigneeId"));
        addSignRequest.setAssigneeName(request.string("assigneeName"));
        TaskResponse task = taskService.addSign(taskId(request), addSignRequest);
        return taskSuccess(request, task);
    }

    private GlobalActionResult retryClosureEffect(GlobalActionRequest request) {
        ProcessClosureDetailResponse.EffectItem effect =
                closureEffectOperationService.retry(effectId(request));
        return effectSuccess(request, effect);
    }

    private GlobalActionResult markClosureEffectHandled(GlobalActionRequest request) {
        ProcessClosureDetailResponse.EffectItem effect =
                closureEffectOperationService.markHandled(effectId(request), request.string("reason"));
        return effectSuccess(request, effect);
    }

    private GlobalActionResult taskSuccess(GlobalActionRequest request, TaskResponse task) {
        return success(request, task.getId(), Map.of(
                "runtimeStatus", task.getStatus(),
                "task", task));
    }

    private GlobalActionResult effectSuccess(GlobalActionRequest request,
                                             ProcessClosureDetailResponse.EffectItem effect) {
        return success(request, effect.getId(), Map.of(
                "runtimeStatus", effect.getStatus(),
                "effect", effect));
    }

    private GlobalActionResult success(GlobalActionRequest request,
                                       String ownerExecutionRef,
                                       Map<String, Object> data) {
        GlobalActionResult response = GlobalActionResult.from(request);
        response.setStatus(ActionStatus.SUCCEEDED);
        response.setOwnerExecutionRef(ownerExecutionRef);
        response.setData(new LinkedHashMap<>(data));
        Object runtimeStatus = data.get("runtimeStatus");
        if (runtimeStatus != null) {
            response.setTargetStatus(String.valueOf(runtimeStatus));
            response.setTargetStatusGroup(
                    ActionStatusMachine.classifyStatusGroup(String.valueOf(runtimeStatus), TERMINAL_STATUSES));
            response.getOwnerExecutionMetadata().put("runtimeStatus", runtimeStatus);
        }
        response.getRefreshScopes().addAll(List.of("document", "actions", "timeline", "workflow"));
        return response;
    }

    private GlobalActionResult unsupported(GlobalActionRequest request) {
        GlobalActionResult response = GlobalActionResult.from(request);
        response.setStatus(ActionStatus.REJECTED);
        response.setMessage("WORKFLOW_ACTION_UNSUPPORTED");
        response.getErrors().add(ActionError.of(
                "WORKFLOW_ACTION_UNSUPPORTED",
                ActionErrorCategory.VALIDATION,
                "WORKFLOW_ACTION_UNSUPPORTED"));
        return response;
    }

    private boolean supported(String actionType) {
        return switch (actionType) {
            case PROCESS_INSTANCE_START, TASK_APPROVE, TASK_REJECT, TASK_TRANSFER,
                 TASK_ADD_SIGN, CLOSURE_RETRY, CLOSURE_MARK_HANDLED -> true;
            default -> false;
        };
    }

    private GlobalActionResult formValidationFailure(GlobalActionRequest request,
                                                     FormDataValidationException exception) {
        GlobalActionResult response = GlobalActionResult.from(request);
        response.setStatus(ActionStatus.REJECTED);
        response.setMessage(exception.getMessage());
        response.getErrors().add(ActionError.of(
                "FORM_DATA_VALIDATION_FAILED",
                ActionErrorCategory.VALIDATION,
                exception.getMessage()));
        response.getData().put("fieldErrors", exception.getFieldErrors());
        return response;
    }

    private GlobalActionResult versionConflictFailure(GlobalActionRequest request,
                                                      ProcessVersionConflictException exception) {
        GlobalActionResult response = GlobalActionResult.from(request);
        response.setStatus(ActionStatus.REJECTED);
        response.setMessage(exception.getMessage());
        response.getErrors().add(ActionError.of(
                "PROCESS_VERSION_CONFLICT",
                ActionErrorCategory.VALIDATION,
                exception.getMessage()));
        response.getData().put("processVersionConflict", exception.getConflict());
        return response;
    }

    private GlobalActionResult businessFailure(GlobalActionRequest request, BizException exception) {
        GlobalActionResult response = GlobalActionResult.from(request);
        boolean serverError = ActionHelpers.isServerError(exception.getCode());
        response.setStatus(serverError ? ActionStatus.FAILED : ActionStatus.REJECTED);
        response.setMessage(exception.getMessage());
        response.getErrors().add(ActionError.of(
                exception.getMessage(),
                serverError ? ActionErrorCategory.EXECUTION : ActionErrorCategory.AUTHORIZATION,
                exception.getMessage()));
        return response;
    }

    private String taskId(GlobalActionRequest request) {
        return required(request, "taskId");
    }

    private String effectId(GlobalActionRequest request) {
        return required(request, "effectId");
    }

    private String operationId(GlobalActionRequest request) {
        return ActionHelpers.firstNonBlank(
                request.string("operationId"), request.getIdempotencyKey(), request.getActionId());
    }

    private String required(GlobalActionRequest request, String key) {
        String value = request.string(key);
        if (value == null || value.isBlank()) {
            throw new BizException(40000, "ACTION_PAYLOAD_" + key.toUpperCase() + "_REQUIRED");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
