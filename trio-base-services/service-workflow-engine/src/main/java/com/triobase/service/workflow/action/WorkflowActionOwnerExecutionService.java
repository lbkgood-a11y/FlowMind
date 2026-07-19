package com.triobase.service.workflow.action;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.action.owner.ActionOwnerDispatchResponse;
import com.triobase.common.action.owner.ActionOwnerExecutor;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.AddSignRequest;
import com.triobase.service.workflow.dto.ApproveTaskRequest;
import com.triobase.service.workflow.dto.ProcessClosureDetailResponse;
import com.triobase.service.workflow.dto.ProcessInstanceResponse;
import com.triobase.service.workflow.dto.RejectTaskRequest;
import com.triobase.service.workflow.dto.StartProcessRequest;
import com.triobase.service.workflow.dto.TaskResponse;
import com.triobase.service.workflow.dto.TransferTaskRequest;
import com.triobase.service.workflow.exception.FormDataValidationException;
import com.triobase.service.workflow.exception.ProcessVersionConflictException;
import com.triobase.service.workflow.service.ClosureEffectOperationService;
import com.triobase.service.workflow.service.ProcessInstanceService;
import com.triobase.service.workflow.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowActionOwnerExecutionService implements ActionOwnerExecutor {

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
    public ActionOwnerDispatchResponse execute(ActionOwnerDispatchRequest request) {
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

    public ActionOwnerGuardResponse guard(ActionOwnerDispatchRequest request) {
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

    private ActionOwnerDispatchResponse startProcess(ActionOwnerDispatchRequest ownerRequest) {
        StartProcessRequest request = new StartProcessRequest();
        request.setProcessPackageId(string(ownerRequest, "processPackageId"));
        request.setVersion(integer(ownerRequest, "version"));
        request.setProcessKey(string(ownerRequest, "processKey"));
        request.setTitle(string(ownerRequest, "title"));
        request.setFormData(map(ownerRequest.getPayload().get("formData")));
        request.setLaunchMode(string(ownerRequest, "launchMode"));
        request.setBusinessType(string(ownerRequest, "businessType"));
        request.setBusinessId(string(ownerRequest, "businessId"));
        request.setIdempotencyKey(firstNonBlank(string(ownerRequest, "idempotencyKey"),
                ownerRequest.getIdempotencyKey()));
        ProcessInstanceResponse processInstance = processInstanceService.startProcess(request);
        return success(ownerRequest, processInstance.getId(), Map.of(
                "runtimeStatus", processInstance.getStatus(),
                "processInstance", processInstance));
    }

    private ActionOwnerDispatchResponse approveTask(ActionOwnerDispatchRequest ownerRequest) {
        ApproveTaskRequest request = new ApproveTaskRequest();
        request.setOperationId(operationId(ownerRequest));
        request.setAction(firstNonBlank(string(ownerRequest, "action"), "APPROVE"));
        request.setComment(string(ownerRequest, "comment"));
        TaskResponse task = taskService.approve(taskId(ownerRequest), request);
        return taskSuccess(ownerRequest, task);
    }

    private ActionOwnerDispatchResponse rejectTask(ActionOwnerDispatchRequest ownerRequest) {
        RejectTaskRequest request = new RejectTaskRequest();
        request.setOperationId(operationId(ownerRequest));
        request.setTargetNodeId(string(ownerRequest, "targetNodeId"));
        request.setComment(string(ownerRequest, "comment"));
        TaskResponse task = taskService.reject(taskId(ownerRequest), request);
        return taskSuccess(ownerRequest, task);
    }

    private ActionOwnerDispatchResponse transferTask(ActionOwnerDispatchRequest ownerRequest) {
        TransferTaskRequest request = new TransferTaskRequest();
        request.setOperationId(operationId(ownerRequest));
        request.setNewAssigneeId(string(ownerRequest, "newAssigneeId"));
        request.setNewAssigneeName(string(ownerRequest, "newAssigneeName"));
        TaskResponse task = taskService.transfer(taskId(ownerRequest), request);
        return taskSuccess(ownerRequest, task);
    }

    private ActionOwnerDispatchResponse addSignTask(ActionOwnerDispatchRequest ownerRequest) {
        AddSignRequest request = new AddSignRequest();
        request.setOperationId(operationId(ownerRequest));
        request.setAssigneeId(string(ownerRequest, "assigneeId"));
        request.setAssigneeName(string(ownerRequest, "assigneeName"));
        TaskResponse task = taskService.addSign(taskId(ownerRequest), request);
        return taskSuccess(ownerRequest, task);
    }

    private ActionOwnerDispatchResponse retryClosureEffect(ActionOwnerDispatchRequest ownerRequest) {
        ProcessClosureDetailResponse.EffectItem effect =
                closureEffectOperationService.retry(effectId(ownerRequest));
        return effectSuccess(ownerRequest, effect);
    }

    private ActionOwnerDispatchResponse markClosureEffectHandled(ActionOwnerDispatchRequest ownerRequest) {
        ProcessClosureDetailResponse.EffectItem effect =
                closureEffectOperationService.markHandled(effectId(ownerRequest), string(ownerRequest, "reason"));
        return effectSuccess(ownerRequest, effect);
    }

    private ActionOwnerDispatchResponse taskSuccess(ActionOwnerDispatchRequest ownerRequest, TaskResponse task) {
        return success(ownerRequest, task.getId(), Map.of(
                "runtimeStatus", task.getStatus(),
                "task", task));
    }

    private ActionOwnerDispatchResponse effectSuccess(ActionOwnerDispatchRequest ownerRequest,
                                                      ProcessClosureDetailResponse.EffectItem effect) {
        return success(ownerRequest, effect.getId(), Map.of(
                "runtimeStatus", effect.getStatus(),
                "effect", effect));
    }

    private ActionOwnerDispatchResponse success(ActionOwnerDispatchRequest ownerRequest,
                                                String ownerExecutionRef,
                                                Map<String, Object> data) {
        ActionOwnerDispatchResponse response = base(ownerRequest);
        response.setStatus(ActionStatus.SUCCEEDED);
        response.setOwnerExecutionRef(ownerExecutionRef);
        response.setData(new LinkedHashMap<>(data));
        Object runtimeStatus = data.get("runtimeStatus");
        if (runtimeStatus != null) {
            response.setTargetStatus(String.valueOf(runtimeStatus));
            response.setTargetStatusGroup(statusGroup(String.valueOf(runtimeStatus)));
            response.getOwnerExecutionMetadata().put("runtimeStatus", runtimeStatus);
        }
        response.getRefreshScopes().addAll(List.of("document", "actions", "timeline", "workflow"));
        return response;
    }

    private ActionOwnerDispatchResponse unsupported(ActionOwnerDispatchRequest ownerRequest) {
        ActionOwnerDispatchResponse response = base(ownerRequest);
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

    private String statusGroup(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.toUpperCase();
        if (normalized.contains("COMPLETED") || normalized.contains("APPROVED")
                || normalized.contains("REJECTED") || normalized.contains("HANDLED")) {
            return "TERMINAL";
        }
        if (normalized.contains("RUNNING") || normalized.contains("PENDING")
                || normalized.contains("ACTIVE")) {
            return "IN_PROGRESS";
        }
        return "BUSINESS";
    }

    private ActionOwnerDispatchResponse formValidationFailure(ActionOwnerDispatchRequest ownerRequest,
                                                              FormDataValidationException exception) {
        ActionOwnerDispatchResponse response = base(ownerRequest);
        response.setStatus(ActionStatus.REJECTED);
        response.setMessage(exception.getMessage());
        response.getErrors().add(ActionError.of(
                "FORM_DATA_VALIDATION_FAILED",
                ActionErrorCategory.VALIDATION,
                exception.getMessage()));
        response.getData().put("fieldErrors", exception.getFieldErrors());
        return response;
    }

    private ActionOwnerDispatchResponse versionConflictFailure(ActionOwnerDispatchRequest ownerRequest,
                                                               ProcessVersionConflictException exception) {
        ActionOwnerDispatchResponse response = base(ownerRequest);
        response.setStatus(ActionStatus.REJECTED);
        response.setMessage(exception.getMessage());
        response.getErrors().add(ActionError.of(
                "PROCESS_VERSION_CONFLICT",
                ActionErrorCategory.VALIDATION,
                exception.getMessage()));
        response.getData().put("processVersionConflict", exception.getConflict());
        return response;
    }

    private ActionOwnerDispatchResponse businessFailure(ActionOwnerDispatchRequest ownerRequest, BizException exception) {
        ActionOwnerDispatchResponse response = base(ownerRequest);
        boolean serverError = isServerError(exception.getCode());
        response.setStatus(serverError ? ActionStatus.FAILED : ActionStatus.REJECTED);
        response.setMessage(exception.getMessage());
        response.getErrors().add(ActionError.of(
                exception.getMessage(),
                serverError ? ActionErrorCategory.EXECUTION : ActionErrorCategory.AUTHORIZATION,
                exception.getMessage()));
        return response;
    }

    private boolean isServerError(int code) {
        return code >= 50_000 || (code >= 500 && code < 600);
    }

    private ActionOwnerDispatchResponse base(ActionOwnerDispatchRequest ownerRequest) {
        ActionOwnerDispatchResponse response = new ActionOwnerDispatchResponse();
        response.setActionId(ownerRequest.getActionId());
        response.setOwnerService(ownerRequest.getOwnerService());
        return response;
    }

    private String taskId(ActionOwnerDispatchRequest request) {
        return required(request, "taskId");
    }

    private String effectId(ActionOwnerDispatchRequest request) {
        return required(request, "effectId");
    }

    private String operationId(ActionOwnerDispatchRequest request) {
        return firstNonBlank(string(request, "operationId"), request.getIdempotencyKey(), request.getActionId());
    }

    private String required(ActionOwnerDispatchRequest request, String key) {
        String value = string(request, key);
        if (!StringUtils.hasText(value)) {
            throw new BizException(40000, "ACTION_PAYLOAD_" + key.toUpperCase() + "_REQUIRED");
        }
        return value;
    }

    private String string(ActionOwnerDispatchRequest request, String key) {
        Object value = request.getPayload().get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private Integer integer(ActionOwnerDispatchRequest request, String key) {
        Object value = request.getPayload().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && !String.valueOf(value).isBlank()) {
            return Integer.parseInt(String.valueOf(value));
        }
        return null;
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
