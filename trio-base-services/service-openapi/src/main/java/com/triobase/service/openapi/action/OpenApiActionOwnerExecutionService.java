package com.triobase.service.openapi.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.action.owner.ActionOwnerDispatchResponse;
import com.triobase.common.action.owner.ActionOwnerExecutor;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.enums.CallbackInboxState;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.OrchestrationExecutionResponse;
import com.triobase.service.openapi.dto.RuntimeAdmissionContext;
import com.triobase.service.openapi.service.CallbackSignalDispatcher;
import com.triobase.service.openapi.service.OrchestrationRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenApiActionOwnerExecutionService implements ActionOwnerExecutor {

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
    public ActionOwnerDispatchResponse execute(ActionOwnerDispatchRequest request) {
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

    public ActionOwnerGuardResponse guard(ActionOwnerDispatchRequest request) {
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

    private ActionOwnerDispatchResponse startOrchestration(ActionOwnerDispatchRequest request) {
        OrchestrationExecutionResponse response = orchestrationRuntimeService.start(
                required(request, "routeKey"),
                Environment.valueOf(required(request, "environment")),
                admission(request),
                required(request, "operation"),
                firstNonBlank(string(request, "idempotencyKey"), request.getIdempotencyKey()),
                payloadNode(request));
        return success(request, response.executionId(), Map.of(
                "runtimeStatus", response.state().name(),
                "orchestration", response));
    }

    private ActionOwnerDispatchResponse cancelOrchestration(ActionOwnerDispatchRequest request) {
        OrchestrationExecutionResponse response = orchestrationRuntimeService.cancel(
                required(request, "executionId"),
                required(request, "applicationClientId"),
                required(request, "reason"));
        return success(request, response.executionId(), Map.of(
                "runtimeStatus", response.state().name(),
                "orchestration", response));
    }

    private ActionOwnerDispatchResponse signalCallback(ActionOwnerDispatchRequest request) {
        CallbackInbox inbox = callbackSignalDispatcher.dispatchInbox(required(request, "inboxId"));
        if (inbox.getInboxState() == CallbackInboxState.SIGNAL_PENDING
                && StringUtils.hasText(inbox.getLastSignalError())) {
            ActionOwnerDispatchResponse response = base(request);
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

    private ActionOwnerDispatchResponse unsupportedStateChanging(ActionOwnerDispatchRequest request) {
        ActionOwnerDispatchResponse response = base(request);
        response.setStatus(ActionStatus.REJECTED);
        response.setMessage("OPENAPI_STATE_CHANGING_SYNC_ACTION_NOT_SUPPORTED");
        response.getErrors().add(ActionError.of(
                "OPENAPI_STATE_CHANGING_SYNC_ACTION_NOT_SUPPORTED",
                ActionErrorCategory.VALIDATION,
                "OPENAPI_STATE_CHANGING_SYNC_ACTION_NOT_SUPPORTED"));
        return response;
    }

    private ActionOwnerDispatchResponse success(ActionOwnerDispatchRequest request,
                                                String ownerExecutionRef,
                                                Map<String, Object> data) {
        ActionOwnerDispatchResponse response = base(request);
        response.setStatus(ActionStatus.SUCCEEDED);
        response.setOwnerExecutionRef(ownerExecutionRef);
        response.setData(new LinkedHashMap<>(data));
        Object runtimeStatus = data.get("runtimeStatus");
        if (runtimeStatus != null) {
            response.setTargetStatus(String.valueOf(runtimeStatus));
            response.setTargetStatusGroup(statusGroup(String.valueOf(runtimeStatus)));
            response.getOwnerExecutionMetadata().put("runtimeStatus", runtimeStatus);
        }
        response.getRefreshScopes().addAll(List.of("document", "actions", "timeline", "relatedTables"));
        return response;
    }

    private ActionOwnerDispatchResponse unsupported(ActionOwnerDispatchRequest request) {
        ActionOwnerDispatchResponse response = base(request);
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

    private String statusGroup(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.toUpperCase();
        if (normalized.contains("COMPLETED") || normalized.contains("SUCCEEDED")
                || normalized.contains("CANCELLED")) {
            return "TERMINAL";
        }
        if (normalized.contains("RUNNING") || normalized.contains("PENDING")
                || normalized.contains("ACTIVE")) {
            return "IN_PROGRESS";
        }
        return "BUSINESS";
    }

    private ActionOwnerDispatchResponse businessFailure(ActionOwnerDispatchRequest request, BizException exception) {
        ActionOwnerDispatchResponse response = base(request);
        boolean serverError = isServerError(exception.getCode());
        response.setStatus(serverError ? ActionStatus.FAILED : ActionStatus.REJECTED);
        response.setRetryable(serverError);
        response.setMessage(exception.getMessage());
        response.getErrors().add(ActionError.of(
                exception.getMessage(),
                serverError ? ActionErrorCategory.EXECUTION : ActionErrorCategory.VALIDATION,
                exception.getMessage()));
        return response;
    }

    private boolean isServerError(int code) {
        return code >= 50_000 || (code >= 500 && code < 600);
    }

    private ActionOwnerDispatchResponse base(ActionOwnerDispatchRequest request) {
        ActionOwnerDispatchResponse response = new ActionOwnerDispatchResponse();
        response.setActionId(request.getActionId());
        response.setOwnerService(request.getOwnerService());
        return response;
    }

    private RuntimeAdmissionContext admission(ActionOwnerDispatchRequest request) {
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

    private JsonNode payloadNode(ActionOwnerDispatchRequest request) {
        Object value = request.getPayload().get("payload");
        return value == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(value);
    }

    private String required(ActionOwnerDispatchRequest request, String key) {
        String value = string(request, key);
        if (!StringUtils.hasText(value)) {
            throw new BizException(40075, "OPENAPI_ACTION_PAYLOAD_" + key.toUpperCase() + "_REQUIRED");
        }
        return value;
    }

    private String string(ActionOwnerDispatchRequest request, String key) {
        return text(request.getPayload().get(key));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return Long.parseLong(String.valueOf(value));
        }
        return 0L;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
