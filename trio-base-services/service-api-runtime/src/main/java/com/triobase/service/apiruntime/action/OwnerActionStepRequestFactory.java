package com.triobase.service.apiruntime.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.util.StringHelpers;
import com.triobase.service.apiruntime.temporal.OpenApiTemporalContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OwnerActionStepRequestFactory {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public GlobalActionRequest from(JsonNode command) {
        JsonNode step = command.path("step");
        JsonNode payload = command.path("payload");
        JsonNode context = command.path("context");
        String executionId = requireText(command, "executionId");
        String stepKey = requireText(step, "key");
        String ownerService = requireText(step, "ownerService");
        String idempotencyKey = idempotencyKey(command, step, context, payload);

        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionId(stableActionId(executionId, stepKey, idempotencyKey));
        request.setActionType(requireText(step, "actionType"));
        request.setSource(ActionSource.API);
        request.setExecutionMode(executionMode(step));
        request.setIdempotencyKey(idempotencyKey);
        request.setPayload(actionPayload(step, payload));
        request.setActor(actor(context));
        request.setTarget(target(step, context, payload, ownerService));
        request.setContext(actionContext(command, step, context));
        return request;
    }

    private Map<String, Object> actionPayload(JsonNode step, JsonNode payload) {
        ObjectNode actionPayload = objectMapper.createObjectNode();
        JsonNode selected = select(payload, step.path("payloadPointer").asText());
        if (selected.isObject()) {
            actionPayload.setAll((ObjectNode) selected.deepCopy());
        } else if (!selected.isMissingNode() && !selected.isNull()) {
            actionPayload.set("value", selected);
        }
        JsonNode staticPayload = step.path("payload");
        if (staticPayload.isObject()) {
            staticPayload.fields().forEachRemaining(field ->
                    actionPayload.set(field.getKey(), field.getValue()));
        }
        return objectMapper.convertValue(actionPayload, PAYLOAD_TYPE);
    }

    private ActionActor actor(JsonNode context) {
        String clientId = context.path(OpenApiTemporalContext.APPLICATION_CLIENT_ID).asText();
        String actorId = StringHelpers.firstNonBlank(clientId, "service-api-runtime");
        ActionActor actor = new ActionActor();
        actor.setType(ActionActorType.SERVICE);
        actor.setId(actorId);
        actor.setDisplayName(actorId);
        actor.setTenantId(context.path(OpenApiTemporalContext.TENANT_ID).asText(null));
        return actor;
    }

    private ActionTarget target(JsonNode step, JsonNode context, JsonNode payload, String ownerService) {
        ActionTarget target = new ActionTarget();
        target.setOwnerService(ownerService);
        target.setType(requireText(step, "targetType"));
        target.setId(StringHelpers.firstNonBlank(
                textAt(payload, step.path("targetIdPointer").asText()),
                step.path("targetId").asText()));
        if (!StringUtils.hasText(target.getId())) {
            throw new BizException(42276, "OPENAPI_OWNER_ACTION_TARGET_REQUIRED");
        }
        target.setVersion(step.path("targetVersion").asText(null));
        target.setTenantId(context.path(OpenApiTemporalContext.TENANT_ID).asText(null));
        return target;
    }

    private ActionContext actionContext(JsonNode command, JsonNode step, JsonNode context) {
        ActionContext actionContext = new ActionContext();
        actionContext.setTenantId(context.path(OpenApiTemporalContext.TENANT_ID).asText(null));
        actionContext.setTraceId(context.path(OpenApiTemporalContext.TRACE_ID).asText(null));
        actionContext.setRequestId(context.path(OpenApiTemporalContext.IDEMPOTENCY_KEY).asText(null));
        actionContext.setCorrelationId(command.path("executionId").asText(null));
        actionContext.getAttributes().put("openapiExecutionId", command.path("executionId").asText());
        actionContext.getAttributes().put("openapiStepKey", step.path("key").asText());
        actionContext.getAttributes().put("openapiReleaseId",
                context.path(OpenApiTemporalContext.RELEASE_ID).asText(null));
        actionContext.getAttributes().put("applicationClientId",
                context.path(OpenApiTemporalContext.APPLICATION_CLIENT_ID).asText(null));
        return actionContext;
    }

    private ActionExecutionMode executionMode(JsonNode step) {
        String mode = step.path("executionMode").asText(ActionExecutionMode.SYNC.name());
        try {
            return ActionExecutionMode.valueOf(mode);
        } catch (Exception exception) {
            throw new BizException(42276, "OPENAPI_OWNER_ACTION_EXECUTION_MODE_INVALID");
        }
    }

    private String idempotencyKey(JsonNode command, JsonNode step, JsonNode context, JsonNode payload) {
        String explicit = StringHelpers.firstNonBlank(
                textAt(payload, step.path("idempotencyKeyPointer").asText()),
                step.path("idempotencyKey").asText());
        String root = StringHelpers.firstNonBlank(
                explicit,
                context.path(OpenApiTemporalContext.IDEMPOTENCY_KEY).asText(),
                command.path("executionId").asText());
        return "openapi:" + root + ':' + command.path("executionId").asText()
                + ':' + step.path("key").asText();
    }

    private JsonNode select(JsonNode payload, String pointer) {
        if (!StringUtils.hasText(pointer)) {
            return payload;
        }
        if (!pointer.startsWith("/")) {
            throw new BizException(42276, "OPENAPI_OWNER_ACTION_POINTER_INVALID");
        }
        return payload.at(pointer);
    }

    private String textAt(JsonNode payload, String pointer) {
        JsonNode value = select(payload, pointer);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private String requireText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (!StringUtils.hasText(value)) {
            throw new BizException(42276, "OPENAPI_OWNER_ACTION_FIELD_REQUIRED:" + field);
        }
        return value.trim();
    }

    private String stableActionId(String executionId, String stepKey, String idempotencyKey) {
        return "act_" + sha256(executionId + ':' + stepKey + ':' + idempotencyKey).substring(0, 32);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash owner action id", exception);
        }
    }
}
