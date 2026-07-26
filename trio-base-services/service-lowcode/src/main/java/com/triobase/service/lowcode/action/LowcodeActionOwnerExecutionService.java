package com.triobase.service.lowcode.action;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerExecutor;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.action.util.ActionHelpers;
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
    public GlobalActionResult execute(GlobalActionRequest request) {
        return switch (request.getActionType()) {
            case LOWCODE_FORM_CREATE, LOWCODE_FORM_SAVE, LOWCODE_FORM_SUBMIT -> executeFormAction(request);
            case LOWCODE_WORKFLOW_RETRY -> executeWorkflowRetry(request);
            default -> unsupported(request);
        };
    }

    public ActionOwnerGuardResponse guard(GlobalActionRequest request) {
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

    private GlobalActionResult executeFormAction(GlobalActionRequest request) {
        GlobalActionResult result = applicationRuntimeService.executeLocalAction(
                request.string("appKey"),
                request.integer("version"),
                request.string("actionCode"),
                request);
        return ownerResult(request, result);
    }

    private GlobalActionResult executeWorkflowRetry(GlobalActionRequest request) {
        RuntimeRetryWorkflowRequest retryRequest = new RuntimeRetryWorkflowRequest();
        retryRequest.setActionCode(request.string("actionCode"));
        retryRequest.setIdempotencyKey(ActionHelpers.firstNonBlank(
                request.string("idempotencyKey"), request.getIdempotencyKey()));
        GlobalActionResult result = applicationRuntimeService.executeLocalWorkflowRetry(
                request.string("appKey"),
                request.integer("version"),
                request.string("instanceId"),
                retryRequest,
                request);
        return ownerResult(request, result);
    }

    private GlobalActionResult ownerResult(GlobalActionRequest request, GlobalActionResult result) {
        GlobalActionResult response = result != null ? result : new GlobalActionResult();
        response.setActionId(request.getActionId());
        response.setActionType(request.getActionType());
        response.setTarget(request.getTarget());
        if (response.getOwnerService() == null) {
            response.setOwnerService(request.getTarget() != null ? request.getTarget().getOwnerService() : null);
        }
        response.setRefreshScopes(response.getRefreshScopes() != null && !response.getRefreshScopes().isEmpty()
                ? response.getRefreshScopes()
                : List.of("document", "actions", "timeline"));
        response.setData(response.getData() != null ? response.getData() : Map.of());
        if ((response.getStatus() == ActionStatus.FAILED || response.getStatus() == ActionStatus.REJECTED)
                && response.getErrors().isEmpty()) {
            response.getErrors().add(ActionError.of(
                    "LOWCODE_ACTION_FAILED",
                    ActionErrorCategory.EXECUTION,
                    ActionHelpers.firstNonBlank(response.getMessage(), "LOWCODE_ACTION_FAILED")));
        }
        return response;
    }

    private boolean supported(String actionType) {
        return switch (actionType) {
            case LOWCODE_FORM_CREATE, LOWCODE_FORM_SAVE, LOWCODE_FORM_SUBMIT, LOWCODE_WORKFLOW_RETRY -> true;
            default -> false;
        };
    }

    private GlobalActionResult unsupported(GlobalActionRequest request) {
        GlobalActionResult response = GlobalActionResult.from(request);
        response.setStatus(ActionStatus.REJECTED);
        response.setMessage("LOWCODE_ACTION_UNSUPPORTED");
        response.getErrors().add(ActionError.of("LOWCODE_ACTION_UNSUPPORTED",
                ActionErrorCategory.VALIDATION,
                "LOWCODE_ACTION_UNSUPPORTED"));
        return response;
    }
}
