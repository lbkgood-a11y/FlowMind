package com.triobase.service.lowcode.action;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.action.owner.ActionOwnerDispatchResponse;
import com.triobase.common.action.owner.ActionOwnerExecutor;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.service.lowcode.dto.RuntimeRetryWorkflowRequest;
import com.triobase.service.lowcode.service.ApplicationRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LowcodeActionOwnerExecutionService implements ActionOwnerExecutor {

    private static final String LOWCODE_FORM_CREATE = "lowcode.form.create";
    private static final String LOWCODE_FORM_SAVE = "lowcode.form.save";
    private static final String LOWCODE_FORM_SUBMIT = "lowcode.form.submit";
    private static final String LOWCODE_WORKFLOW_RETRY = "lowcode.workflow.retry";

    private final ApplicationRuntimeService applicationRuntimeService;

    @Override
    public String actionType() {
        return "lowcode.*";
    }

    @Override
    public ActionOwnerDispatchResponse execute(ActionOwnerDispatchRequest request) {
        return switch (request.getActionType()) {
            case LOWCODE_FORM_CREATE, LOWCODE_FORM_SAVE, LOWCODE_FORM_SUBMIT -> executeFormAction(request);
            case LOWCODE_WORKFLOW_RETRY -> executeWorkflowRetry(request);
            default -> unsupported(request);
        };
    }

    public ActionOwnerGuardResponse guard(ActionOwnerDispatchRequest request) {
        if (request == null || !supported(request.getActionType())) {
            return ActionOwnerGuardResponse.denied(
                    "LOWCODE_ACTION_UNSUPPORTED",
                    "LOWCODE_ACTION_UNSUPPORTED",
                    List.of(ActionError.of(
                            "LOWCODE_ACTION_UNSUPPORTED",
                            ActionErrorCategory.VALIDATION,
                            "LOWCODE_ACTION_UNSUPPORTED")));
        }
        return ActionOwnerGuardResponse.allowed("LOWCODE_ACTION_SUPPORTED");
    }

    private ActionOwnerDispatchResponse executeFormAction(ActionOwnerDispatchRequest ownerRequest) {
        GlobalActionResult result = applicationRuntimeService.executeLocalAction(
                string(ownerRequest, "appKey"),
                integer(ownerRequest, "version"),
                string(ownerRequest, "actionCode"),
                globalActionRequest(ownerRequest));
        return toOwnerResponse(ownerRequest, result);
    }

    private ActionOwnerDispatchResponse executeWorkflowRetry(ActionOwnerDispatchRequest ownerRequest) {
        RuntimeRetryWorkflowRequest retryRequest = new RuntimeRetryWorkflowRequest();
        retryRequest.setActionCode(string(ownerRequest, "actionCode"));
        retryRequest.setIdempotencyKey(firstNonBlank(string(ownerRequest, "idempotencyKey"),
                ownerRequest.getIdempotencyKey()));
        GlobalActionResult result = applicationRuntimeService.executeLocalWorkflowRetry(
                string(ownerRequest, "appKey"),
                integer(ownerRequest, "version"),
                string(ownerRequest, "instanceId"),
                retryRequest,
                globalActionRequest(ownerRequest));
        return toOwnerResponse(ownerRequest, result);
    }

    private GlobalActionRequest globalActionRequest(ActionOwnerDispatchRequest ownerRequest) {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionId(ownerRequest.getActionId());
        request.setActionType(ownerRequest.getActionType());
        request.setSource(ownerRequest.getSource());
        request.setExecutionMode(ownerRequest.getExecutionMode());
        request.setIdempotencyKey(ownerRequest.getIdempotencyKey());
        request.setPayload(ownerRequest.getPayload() != null ? ownerRequest.getPayload() : Map.of());
        return request;
    }

    private ActionOwnerDispatchResponse toOwnerResponse(ActionOwnerDispatchRequest ownerRequest,
                                                        GlobalActionResult result) {
        ActionOwnerDispatchResponse response = new ActionOwnerDispatchResponse();
        response.setActionId(ownerRequest.getActionId());
        response.setOwnerService(ownerRequest.getOwnerService());
        response.setStatus(result.getStatus());
        response.setRetryable(result.isRetryable());
        response.setMessage(result.getMessage());
        response.setOwnerExecutionRef(result.getOwnerExecutionRef());
        response.setOwnerExecutionMetadata(result.getOwnerExecutionMetadata());
        response.setTargetStatus(result.getTargetStatus());
        response.setTargetStatusGroup(result.getTargetStatusGroup());
        response.setRefreshScopes(result.getRefreshScopes() != null && !result.getRefreshScopes().isEmpty()
                ? result.getRefreshScopes()
                : List.of("document", "actions", "timeline"));
        response.setData(result.getData() != null ? result.getData() : Map.of());
        if (result.getErrors() != null) {
            response.getErrors().addAll(result.getErrors());
        }
        if (response.getStatus() == ActionStatus.FAILED || response.getStatus() == ActionStatus.REJECTED) {
            if (response.getErrors().isEmpty()) {
                response.getErrors().add(ActionError.of(
                        "LOWCODE_ACTION_FAILED",
                        ActionErrorCategory.EXECUTION,
                        firstNonBlank(result.getMessage(), "LOWCODE_ACTION_FAILED")));
            }
        }
        return response;
    }

    private boolean supported(String actionType) {
        return switch (actionType) {
            case LOWCODE_FORM_CREATE, LOWCODE_FORM_SAVE, LOWCODE_FORM_SUBMIT, LOWCODE_WORKFLOW_RETRY -> true;
            default -> false;
        };
    }

    private ActionOwnerDispatchResponse unsupported(ActionOwnerDispatchRequest ownerRequest) {
        ActionOwnerDispatchResponse response = new ActionOwnerDispatchResponse();
        response.setActionId(ownerRequest.getActionId());
        response.setOwnerService(ownerRequest.getOwnerService());
        response.setStatus(ActionStatus.REJECTED);
        response.setMessage("LOWCODE_ACTION_UNSUPPORTED");
        response.getErrors().add(ActionError.of("LOWCODE_ACTION_UNSUPPORTED",
                ActionErrorCategory.VALIDATION,
                "LOWCODE_ACTION_UNSUPPORTED"));
        return response;
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

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }
}
