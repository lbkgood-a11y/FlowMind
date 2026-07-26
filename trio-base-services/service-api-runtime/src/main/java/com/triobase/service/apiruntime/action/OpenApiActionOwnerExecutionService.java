package com.triobase.service.apiruntime.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.triobase.common.openapi.entity.CallbackInbox;
import com.triobase.common.openapi.enums.CallbackInboxState;
import com.triobase.common.openapi.enums.Environment;
import com.triobase.common.openapi.dto.OrchestrationExecutionResponse;
import com.triobase.common.openapi.dto.RuntimeAdmissionContext;
import com.triobase.service.apiruntime.service.CallbackSignalDispatcher;
import com.triobase.service.apiruntime.service.OrchestrationRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OpenApiActionOwnerExecutionService implements ActionOwnerExecutor {

    private static final Set<String> TERMINAL_STATUSES =
            Set.of("COMPLETED", "SUCCEEDED", "CANCELLED");

    private static final String ORCHESTRATION_START = "integration.orchestration.start";
    private static final String ORCHESTRATION_CANCEL = "integration.orchestration.cancel";
    private static final String STATE_CHANGING_INVOCATION = "integration.invocation.stateChanging";
    private static final String CALLBACK_SIGNAL = "integration.callback.signal";

    private final OrchestrationRuntimeService orchestrationRuntimeService;
    private final CallbackSignalDispatcher callbackSignalDispatcher;
    private final ObjectMapper objectMapper;

    @Override
    public String actionType() {
        return "integration.*";
    }

    @Override
    public GlobalActionResult execute(GlobalActionRequest request) {
        try {
            return switch (request.getActionType()) {
                case ORCHESTRATION_START -> startOrchestration(request);
                case ORCHESTRATION_CANCEL -> cancelOrchestration(request);
                case CALLBACK_SIGNAL -> signalCallback(request);
                case STATE_CHANGING_INVOCATION -> unsupportedStateChanging(request);
                default -> unsupported(request);
            };
        } catch (BizException exception) {
            return businessFailure(request, exception);
        }
    }

    public ActionOwnerGuardResponse guard(GlobalActionRequest request) {
        if (request == null || !supported(request.getActionType())) {
            return ActionOwnerGuardResponse.denied(
                    "OPENAPI_ACTION_UNSUPPORTED",
                    "OPENAPI_ACTION_UNSUPPORTED",
                    List.of(ActionError.of(
                            "OPENAPI_ACTION_UNSUPPORTED",
                            ActionErrorCategory.VALIDATION,
                            "OPENAPI_ACTION_UNSUPPORTED")));
        }
        if (STATE_CHANGING_INVOCATION.equals(request.getActionType())) {
            return ActionOwnerGuardResponse.denied(
                    "OPENAPI_STATE_CHANGING_SYNC_ACTION_NOT_SUPPORTED",
                    "OPENAPI_STATE_CHANGING_SYNC_ACTION_NOT_SUPPORTED",
                    List.of(ActionError.of(
                            "OPENAPI_STATE_CHANGING_SYNC_ACTION_NOT_SUPPORTED",
                            ActionErrorCategory.VALIDATION,
                            "OPENAPI_STATE_CHANGING_SYNC_ACTION_NOT_SUPPORTED")));
        }
        return ActionOwnerGuardResponse.allowed("OPENAPI_ACTION_SUPPORTED");
    }

    private GlobalActionResult startOrchestration(GlobalActionRequest request) {
        OrchestrationExecutionResponse response = orchestrationRuntimeService.start(
                required(request, "routeKey"),
                Environment.valueOf(required(request, "environment")),
                admission(request),
                required(request, "operation"),
                ActionHelpers.firstNonBlank(request.string("idempotencyKey"), request.getIdempotencyKey()),
                payloadNode(request));
        return success(request, response.executionId(), Map.of(
                "runtimeStatus", response.state().name(),
                "orchestration", response));
    }

    private GlobalActionResult cancelOrchestration(GlobalActionRequest request) {
        OrchestrationExecutionResponse response = orchestrationRuntimeService.cancel(
                required(request, "executionId"),
                required(request, "applicationClientId"),
                required(request, "reason"));
        return success(request, response.executionId(), Map.of(
                "runtimeStatus", response.state().name(),
                "orchestration", response));
    }

    private GlobalActionResult signalCallback(GlobalActionRequest request) {
        CallbackInbox inbox = callbackSignalDispatcher.dispatchInbox(required(request, "inboxId"));
        if (inbox.getInboxState() == CallbackInboxState.SIGNAL_PENDING
                && inbox.getLastSignalError() != null && !inbox.getLastSignalError().isBlank()) {
            GlobalActionResult response = GlobalActionResult.from(request);
            response.setStatus(ActionStatus.FAILED);
            response.setRetryable(true);
            response.setMessage(inbox.getLastSignalError());
            response.setOwnerExecutionRef(inbox.getId());
            response.getData().put("runtimeStatus", inbox.getInboxState().name());
            response.getData().put("inbox", inbox);
            response.getErrors().add(ActionError.of(
                    "OPENAPI_CALLBACK_SIGNAL_DEFERRED",
                    ActionErrorCategory.DISPATCH,
                    inbox.getLastSignalError()));
            return response;
        }
        return success(request, inbox.getId(), Map.of(
                "runtimeStatus", inbox.getInboxState().name(),
                "inbox", inbox));
    }

    private GlobalActionResult unsupportedStateChanging(GlobalActionRequest request) {
        GlobalActionResult response = GlobalActionResult.from(request);
        response.setStatus(ActionStatus.REJECTED);
        response.setMessage("OPENAPI_STATE_CHANGING_SYNC_ACTION_NOT_SUPPORTED");
        response.getErrors().add(ActionError.of(
                "OPENAPI_STATE_CHANGING_SYNC_ACTION_NOT_SUPPORTED",
                ActionErrorCategory.VALIDATION,
                "OPENAPI_STATE_CHANGING_SYNC_ACTION_NOT_SUPPORTED"));
        return response;
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
        response.getRefreshScopes().addAll(List.of("document", "actions", "timeline", "relatedTables"));
        return response;
    }

    private GlobalActionResult unsupported(GlobalActionRequest request) {
        GlobalActionResult response = GlobalActionResult.from(request);
        response.setStatus(ActionStatus.REJECTED);
        response.setMessage("OPENAPI_ACTION_UNSUPPORTED");
        response.getErrors().add(ActionError.of(
                "OPENAPI_ACTION_UNSUPPORTED",
                ActionErrorCategory.VALIDATION,
                "OPENAPI_ACTION_UNSUPPORTED"));
        return response;
    }

    private boolean supported(String actionType) {
        return switch (actionType) {
            case ORCHESTRATION_START, ORCHESTRATION_CANCEL, STATE_CHANGING_INVOCATION, CALLBACK_SIGNAL -> true;
            default -> false;
        };
    }

    private GlobalActionResult businessFailure(GlobalActionRequest request, BizException exception) {
        GlobalActionResult response = GlobalActionResult.from(request);
        boolean serverError = ActionHelpers.isServerError(exception.getCode());
        response.setStatus(serverError ? ActionStatus.FAILED : ActionStatus.REJECTED);
        response.setRetryable(serverError);
        response.setMessage(exception.getMessage());
        response.getErrors().add(ActionError.of(
                exception.getMessage(),
                serverError ? ActionErrorCategory.EXECUTION : ActionErrorCategory.VALIDATION,
                exception.getMessage()));
        return response;
    }

    private RuntimeAdmissionContext admission(GlobalActionRequest request) {
        Object value = request.getPayload().get("admission");
        Map<?, ?> map = value instanceof Map<?, ?> actual ? actual : Map.of();
        return new RuntimeAdmissionContext(
                text(map.get("tenantId")),
                Environment.valueOf(text(map.get("environment"))),
                text(map.get("applicationClientId")),
                text(map.get("subscriptionId")),
                longValue(map.get("policyVersion")),
                longValue(map.get("maxConcurrency")),
                longValue(map.get("maxActiveWorkflows")));
    }

    private JsonNode payloadNode(GlobalActionRequest request) {
        Object value = request.getPayload().get("payload");
        return value == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(value);
    }

    private String required(GlobalActionRequest request, String key) {
        String value = request.string(key);
        if (value == null || value.isBlank()) {
            throw new BizException(40075, "OPENAPI_ACTION_PAYLOAD_" + key.toUpperCase() + "_REQUIRED");
        }
        return value;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null && !String.valueOf(value).isBlank()) {
            return Long.parseLong(String.valueOf(value));
        }
        return 0L;
    }
}
