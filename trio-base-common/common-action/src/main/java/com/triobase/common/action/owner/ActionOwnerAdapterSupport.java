package com.triobase.common.action.owner;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionResult;

import java.util.List;
import java.util.Map;

public class ActionOwnerAdapterSupport {

    public ActionOwnerDispatchResponse success(ActionOwnerDispatchRequest request,
                                               String ownerExecutionRef,
                                               Map<String, Object> data) {
        ActionOwnerDispatchResponse response = base(request, ActionStatus.SUCCEEDED);
        response.setOwnerExecutionRef(ownerExecutionRef);
        response.setData(data != null ? data : Map.of());
        return response;
    }

    public ActionOwnerDispatchResponse accepted(ActionOwnerDispatchRequest request,
                                                String ownerExecutionRef,
                                                Map<String, Object> data) {
        ActionOwnerDispatchResponse response = base(request, ActionStatus.ACCEPTED);
        response.setOwnerExecutionRef(ownerExecutionRef);
        response.setData(data != null ? data : Map.of());
        return response;
    }

    public ActionOwnerDispatchResponse guardDenied(ActionOwnerDispatchRequest request,
                                                   String code,
                                                   String message) {
        ActionOwnerDispatchResponse response = base(request, ActionStatus.REJECTED);
        response.setMessage(message);
        response.getErrors().add(ActionError.of(code, ActionErrorCategory.GUARD, message));
        return response;
    }

    public ActionOwnerDispatchResponse guardDenied(ActionOwnerDispatchRequest request,
                                                   ActionOwnerGuardResponse guard) {
        String guardCode = guard != null ? guard.getGuardCode() : null;
        String message = guard != null ? guard.getMessage() : null;
        ActionOwnerDispatchResponse response = base(request, ActionStatus.REJECTED);
        response.setMessage(message);
        response.getData().put("guard", Map.of(
                "guardCode", guardCode != null ? guardCode : "",
                "allowed", false,
                "message", message != null ? message : ""));
        if (guard != null && guard.getErrors() != null && !guard.getErrors().isEmpty()) {
            response.getErrors().addAll(guard.getErrors());
        } else {
            response.getErrors().add(ActionError.of(
                    guardCode != null ? guardCode : "ACTION_GUARD_DENIED",
                    ActionErrorCategory.GUARD,
                    message != null ? message : "ACTION_GUARD_DENIED"));
        }
        return response;
    }

    public ActionOwnerDispatchResponse failure(ActionOwnerDispatchRequest request,
                                               String code,
                                               String message,
                                               boolean retryable) {
        ActionOwnerDispatchResponse response = base(request, ActionStatus.FAILED);
        response.setMessage(message);
        response.setRetryable(retryable);
        response.getErrors().add(ActionError.of(code, ActionErrorCategory.EXECUTION, message));
        return response;
    }

    public GlobalActionResult toGlobalResult(ActionOwnerDispatchResponse response) {
        GlobalActionResult result = new GlobalActionResult();
        result.setActionId(response.getActionId());
        result.setStatus(response.getStatus());
        result.setOwnerService(response.getOwnerService());
        result.setOwnerExecutionRef(response.getOwnerExecutionRef());
        result.setOwnerExecutionMetadata(response.getOwnerExecutionMetadata());
        result.setRetryable(response.isRetryable());
        result.setMessage(response.getMessage());
        result.setTargetStatus(response.getTargetStatus());
        result.setTargetStatusGroup(response.getTargetStatusGroup());
        result.setRefreshScopes(response.getRefreshScopes());
        result.setData(response.getData());
        result.setErrors(response.getErrors() != null ? response.getErrors() : List.of());
        return result;
    }

    private ActionOwnerDispatchResponse base(ActionOwnerDispatchRequest request, ActionStatus status) {
        ActionOwnerDispatchResponse response = new ActionOwnerDispatchResponse();
        response.setActionId(request.getActionId());
        response.setOwnerService(request.getOwnerService());
        response.setStatus(status);
        return response;
    }
}
