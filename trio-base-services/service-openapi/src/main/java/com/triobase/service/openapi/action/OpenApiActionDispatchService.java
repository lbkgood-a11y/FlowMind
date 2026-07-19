package com.triobase.service.openapi.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.OrchestrationExecutionResponse;
import com.triobase.service.openapi.dto.RuntimeAdmissionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenApiActionDispatchService {

    private static final String OWNER_SERVICE = "service-openapi";

    private final OpenApiGlobalActionClient actionClient;
    private final ObjectMapper objectMapper;

    public OrchestrationExecutionResponse startOrchestration(String routeKey,
                                                             Environment environment,
                                                             RuntimeAdmissionContext admission,
                                                             String operation,
                                                             String idempotencyKey,
                                                             JsonNode payload) {
        Map<String, Object> actionPayload = runtimePayload(routeKey, environment, admission, operation, payload);
        actionPayload.put("idempotencyKey", idempotencyKey);
        GlobalActionResult result = actionClient.submit(actionRequest(
                "integration.orchestration.start",
                ActionSource.API,
                ActionActorType.SERVICE,
                admission.applicationClientId(),
                admission.applicationClientId(),
                admission.tenantId(),
                ActionExecutionMode.WORKFLOW,
                "INTEGRATION_ROUTE",
                routeKey,
                idempotencyKey,
                actionPayload));
        return resultData(result, "orchestration", OrchestrationExecutionResponse.class);
    }

    public CallbackInbox signalCallback(String inboxId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inboxId", inboxId);
        GlobalActionResult result = actionClient.submit(actionRequest(
                "integration.callback.signal",
                ActionSource.SCHEDULER,
                ActionActorType.SERVICE,
                OWNER_SERVICE,
                OWNER_SERVICE,
                null,
                ActionExecutionMode.SIGNAL,
                "OPENAPI_CALLBACK_INBOX",
                inboxId,
                null,
                payload));
        return resultData(result, "inbox", CallbackInbox.class);
    }

    public OrchestrationExecutionResponse cancelOrchestration(String executionId,
                                                              String applicationClientId,
                                                              String idempotencyKey,
                                                              String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("executionId", executionId);
        payload.put("applicationClientId", applicationClientId);
        payload.put("reason", reason);
        String effectiveKey = firstNonBlank(idempotencyKey, cancelIdempotencyKey(executionId, reason));
        GlobalActionResult result = actionClient.submit(actionRequest(
                "integration.orchestration.cancel",
                ActionSource.API,
                ActionActorType.SERVICE,
                applicationClientId,
                applicationClientId,
                SecurityContextHolder.getTenantId(),
                ActionExecutionMode.SIGNAL,
                "INTEGRATION_EXECUTION",
                executionId,
                effectiveKey,
                payload));
        return resultData(result, "orchestration", OrchestrationExecutionResponse.class);
    }

    private Map<String, Object> runtimePayload(String routeKey,
                                               Environment environment,
                                               RuntimeAdmissionContext admission,
                                               String operation,
                                               JsonNode payload) {
        Map<String, Object> actionPayload = new LinkedHashMap<>();
        actionPayload.put("routeKey", routeKey);
        actionPayload.put("environment", environment.name());
        actionPayload.put("operation", operation);
        actionPayload.put("admission", Map.of(
                "tenantId", admission.tenantId(),
                "environment", admission.environment().name(),
                "applicationClientId", admission.applicationClientId(),
                "subscriptionId", admission.subscriptionId(),
                "policyVersion", admission.policyVersion(),
                "maxConcurrency", admission.maxConcurrency(),
                "maxActiveWorkflows", admission.maxActiveWorkflows()));
        actionPayload.put("payload", payload == null ? objectMapper.createObjectNode() : payload);
        return actionPayload;
    }

    private GlobalActionRequest actionRequest(String actionType,
                                              ActionSource source,
                                              ActionActorType actorType,
                                              String actorId,
                                              String actorName,
                                              String tenantId,
                                              ActionExecutionMode mode,
                                              String targetType,
                                              String targetId,
                                              String idempotencyKey,
                                              Map<String, Object> payload) {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionType(actionType);
        request.setSource(source);
        request.setExecutionMode(mode);
        request.setIdempotencyKey(normalize(idempotencyKey));
        request.setPayload(payload != null ? payload : Map.of());

        ActionActor actor = new ActionActor();
        actor.setType(actorType);
        actor.setId(actorId);
        actor.setDisplayName(actorName);
        actor.setTenantId(tenantId);
        request.setActor(actor);

        ActionTarget target = new ActionTarget();
        target.setType(targetType);
        target.setId(targetId);
        target.setOwnerService(OWNER_SERVICE);
        target.setTenantId(tenantId);
        request.setTarget(target);

        ActionContext context = new ActionContext();
        context.setTenantId(tenantId);
        context.setTraceId(TraceUtil.getTraceId());
        request.setContext(context);
        return request;
    }

    private <T> T resultData(GlobalActionResult result, String key, Class<T> type) {
        if (result != null
                && result.getStatus() == ActionStatus.SUCCEEDED
                && result.getData() != null
                && result.getData().get(key) != null) {
            return objectMapper.convertValue(result.getData().get(key), type);
        }
        ActionError error = result != null && result.getErrors() != null && !result.getErrors().isEmpty()
                ? result.getErrors().getFirst() : null;
        throw new BizException(
                result != null && result.getStatus() == ActionStatus.REJECTED ? 40075 : 50275,
                firstNonBlank(result != null ? result.getMessage() : null,
                        error != null ? error.getCode() : null,
                        "OPENAPI_ACTION_FAILED"));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String cancelIdempotencyKey(String executionId, String reason) {
        return "cancel:" + normalize(executionId) + ":" + hashText(reason);
    }

    private String hashText(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash action idempotency key", exception);
        }
    }
}
