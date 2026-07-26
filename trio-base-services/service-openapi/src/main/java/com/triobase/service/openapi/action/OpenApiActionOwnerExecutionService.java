package com.triobase.service.openapi.action;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerExecutor;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OpenApiActionOwnerExecutionService implements ActionOwnerExecutor {

    private static final String OWNER_SERVICE = "service-openapi";
    private static final String ACTION_TYPE_PATTERN = "integration.*";
    private static final String ERROR_RUNTIME_ADAPTER_NOT_ENABLED = "OPENAPI_RUNTIME_ADAPTER_NOT_ENABLED";
    private static final String ERROR_UNSUPPORTED_ACTION = "OPENAPI_ACTION_UNSUPPORTED";
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            "integration.orchestration.start",
            "integration.orchestration.cancel",
            "integration.invocation.stateChanging",
            "integration.callback.signal");

    @Override
    public String actionType() {
        return ACTION_TYPE_PATTERN;
    }

    public ActionOwnerGuardResponse guard(GlobalActionRequest request) {
        String code = errorCode(request);
        ActionErrorCategory category = ERROR_UNSUPPORTED_ACTION.equals(code)
                ? ActionErrorCategory.VALIDATION
                : ActionErrorCategory.GUARD;
        return ActionOwnerGuardResponse.denied(
                code,
                code,
                List.of(ActionError.of(code, category, code)));
    }

    @Override
    public GlobalActionResult execute(GlobalActionRequest request) {
        String code = errorCode(request);
        ActionErrorCategory category = ERROR_UNSUPPORTED_ACTION.equals(code)
                ? ActionErrorCategory.VALIDATION
                : ActionErrorCategory.EXECUTION;
        GlobalActionResult result = GlobalActionResult.from(request);
        result.setOwnerService(OWNER_SERVICE);
        result.setStatus(ActionStatus.REJECTED);
        result.setRetryable(false);
        result.setMessage(code);
        result.setErrors(List.of(ActionError.of(code, category, code)));
        result.setData(Map.of("runtimeAdapterEnabled", false));
        Instant now = Instant.now();
        result.setCreatedAt(now);
        result.setUpdatedAt(now);
        return result;
    }

    private String errorCode(GlobalActionRequest request) {
        String actionType = request != null ? request.getActionType() : null;
        if (actionType == null || !SUPPORTED_ACTIONS.contains(actionType)) {
            return ERROR_UNSUPPORTED_ACTION;
        }
        return ERROR_RUNTIME_ADAPTER_NOT_ENABLED;
    }
}
