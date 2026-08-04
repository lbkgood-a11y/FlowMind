package com.triobase.service.apiruntime.temporal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.openapi.entity.ConnectorVersion;
import com.triobase.common.openapi.entity.ExecutionStepAttempt;
import com.triobase.common.openapi.entity.IntegrationExecution;
import com.triobase.common.openapi.entity.OrchestrationVersion;
import com.triobase.common.openapi.entity.ReleaseSnapshot;
import com.triobase.common.openapi.entity.RouteVersion;
import com.triobase.common.openapi.enums.AuthenticationType;
import com.triobase.common.openapi.enums.ExecutionState;
import com.triobase.common.openapi.enums.VersionLifecycleState;
import com.triobase.service.apiruntime.infrastructure.mapper.ConnectorVersionMapper;
import com.triobase.service.apiruntime.infrastructure.mapper.ExecutionStepAttemptMapper;
import com.triobase.service.apiruntime.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.apiruntime.infrastructure.mapper.IdempotencyRecordMapper;
import com.triobase.service.apiruntime.infrastructure.mapper.OrchestrationVersionMapper;
import com.triobase.service.apiruntime.infrastructure.mapper.ReleaseSnapshotMapper;
import com.triobase.service.apiruntime.infrastructure.mapper.RouteVersionMapper;
import com.triobase.common.openapi.credential.CredentialMaterial;
import com.triobase.common.openapi.credential.CredentialProvider;
import com.triobase.service.apiruntime.action.OwnerActionDispatchException;
import com.triobase.service.apiruntime.action.OwnerActionStepRequestFactory;
import com.triobase.service.apiruntime.action.OwnerHostedActionDispatchClient;
import com.triobase.service.apiruntime.integration.OutboundIntegrationClient;
import com.triobase.common.openapi.integration.SensitiveDataRedactor;
import com.triobase.service.apiruntime.service.CompiledMappingExecutor;
import com.triobase.service.apiruntime.service.RuntimeBudgetService;
import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 集成编排 Activity 的受治理实现。
 *
 * <p>发布快照、租户和固定依赖在任何外部调用前校验；连接器凭证只能通过 CredentialProvider
 * 获取，Owner 副作用只能通过 owner-hosted Action。Activity 重试依赖执行步骤和幂等记录，
 * 禁止把“最大重试次数”当作防重机制。</p>
 */
@Component
@ActivityImpl(taskQueues = "service-api-runtime")
@RequiredArgsConstructor
public class IntegrationOrchestrationActivitiesImpl implements IntegrationOrchestrationActivities {

    private final ReleaseSnapshotMapper releaseMapper;
    private final RouteVersionMapper routeMapper;
    private final OrchestrationVersionMapper orchestrationMapper;
    private final ConnectorVersionMapper connectorMapper;
    private final IntegrationExecutionMapper executionMapper;
    private final IdempotencyRecordMapper idempotencyMapper;
    private final ExecutionStepAttemptMapper attemptMapper;
    private final CompiledMappingExecutor mappingExecutor;
    private final CredentialProvider credentialProvider;
    private final OutboundIntegrationClient outboundClient;
    private final OwnerHostedActionDispatchClient ownerActionClient;
    private final OwnerActionStepRequestFactory ownerActionRequestFactory;
    private final SensitiveDataRedactor redactor;
    private final RuntimeBudgetService runtimeBudgetService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public String loadRelease(String commandJson) {
        JsonNode command = read(commandJson);
        ReleaseSnapshot release = releaseMapper.selectById(command.path("releaseId").asText());
        if (release == null || release.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw nonRetryable("OPENAPI_ORCHESTRATION_RELEASE_NOT_PUBLISHED");
        }
        String tenantId = command.path("context").path(OpenApiTemporalContext.TENANT_ID).asText();
        if (StringUtils.hasText(tenantId) && !tenantId.equals(release.getTenantId())) {
            // Workflow 命令中的租户必须与不可变发布快照一致，禁止跨租户复用 releaseId。
            throw nonRetryable("OPENAPI_ORCHESTRATION_TENANT_MISMATCH");
        }
        RouteVersion route = routeMapper.selectById(release.getRouteVersionId());
        OrchestrationVersion orchestration = route == null ? null
                : orchestrationMapper.selectById(route.getOrchestrationVersionId());
        if (route == null || orchestration == null
                || orchestration.getLifecycleState() != VersionLifecycleState.PUBLISHED
                || !orchestration.getId().equals(
                release.getPinnedDependencies().path("orchestrationVersionId").asText())) {
            throw nonRetryable("OPENAPI_ORCHESTRATION_RELEASE_DEPENDENCY_INVALID");
        }
        ObjectNode loaded = objectMapper.createObjectNode();
        loaded.put("executionId", command.path("executionId").asText());
        loaded.set("context", command.path("context"));
        loaded.set("payload", command.path("payload"));
        loaded.set("definition", orchestration.getDefinitionContent());
        return loaded.toString();
    }

    @Override
    @Transactional
    public String transform(String stepCommandJson) {
        JsonNode command = read(stepCommandJson);
        JsonNode step = command.path("step");
        LocalDateTime started = LocalDateTime.now();
        try {
            JsonNode output = mappingExecutor.execute(
                    step.path("mappingVersionId").asText(), command.path("payload"));
            record(command, "TRANSFORM", "SUCCEEDED", started, null, null,
                    JsonNodeFactory.instance.objectNode().put("outputValidated", true));
            return objectMapper.createObjectNode().set("payload", output).toString();
        } catch (BizException exception) {
            record(command, "TRANSFORM", "FAILED", started, null,
                    String.valueOf(exception.getCode()), evidence(false, exception.getMessage()));
            throw nonRetryable(sanitize(exception.getMessage()));
        }
    }

    @Override
    @Transactional
    public String invokeConnector(String stepCommandJson) {
        JsonNode command = read(stepCommandJson);
        JsonNode step = command.path("step");
        ConnectorVersion connector = connectorMapper.selectById(step.path("connectorVersionId").asText());
        if (connector == null || connector.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw nonRetryable("OPENAPI_ORCHESTRATION_CONNECTOR_NOT_PUBLISHED");
        }
        LocalDateTime started = LocalDateTime.now();
        try {
            CredentialMaterial credential = connector.getAuthenticationType() == AuthenticationType.NONE
                    ? null : credentialProvider.resolve(connector.getSecretReference());
            String idempotencyKey = activityIdempotencyKey(command);
            Map<String, List<String>> headers = Map.of(
                    "Idempotency-Key", List.of(idempotencyKey),
                    "X-B3-TraceId", List.of(command.path("context").path(
                            OpenApiTemporalContext.TRACE_ID).asText("unknown")));
            OutboundIntegrationClient.OutboundResponse response = outboundClient.execute(
                    new OutboundIntegrationClient.OutboundRequest(
                            connector, command.path("payload"), headers, credential));
            boolean success = response.status() >= 200 && response.status() < 300;
            boolean retryable = response.status() == 408 || response.status() == 429
                    || response.status() >= 500;
            ObjectNode evidence = JsonNodeFactory.instance.objectNode()
                    .put("responseBytes", response.body() == null ? 0 : response.body().length)
                    .put("retryable", retryable)
                    .put("temporalAttempt", Activity.getExecutionContext().getInfo().getAttempt());
            record(command, "INVOKE", success ? "SUCCEEDED" : "FAILED", started,
                    response.status(), success ? null : "PARTNER_HTTP_" + response.status(), evidence);
            if (!success) {
                if (retryable) {
                    throw ApplicationFailure.newFailure(
                            "OPENAPI_PARTNER_TRANSIENT_FAILURE", "PARTNER_TRANSIENT");
                }
                throw nonRetryable("OPENAPI_PARTNER_NON_RETRYABLE_FAILURE");
            }
            JsonNode responsePayload = response.body() == null || response.body().length == 0
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(new String(response.body(), StandardCharsets.UTF_8));
            return objectMapper.createObjectNode().set("payload", responsePayload).toString();
        } catch (ApplicationFailure failure) {
            throw failure;
        } catch (BizException exception) {
            record(command, "INVOKE", "FAILED", started, null,
                    String.valueOf(exception.getCode()), evidence(true, exception.getMessage()));
            throw ApplicationFailure.newFailure(
                    sanitize(exception.getMessage()), "CONNECTOR_TRANSIENT");
        } catch (Exception exception) {
            record(command, "INVOKE", "FAILED", started, null,
                    "CONNECTOR_TRANSPORT", evidence(true, exception.getMessage()));
            throw ApplicationFailure.newFailure(
                    "OPENAPI_CONNECTOR_TRANSPORT_FAILURE", "CONNECTOR_TRANSIENT");
        }
    }

    @Override
    @Transactional
    public String invokeOwnerAction(String stepCommandJson) {
        JsonNode command = read(stepCommandJson);
        LocalDateTime started = LocalDateTime.now();
        GlobalActionRequest request = null;
        try {
            request = ownerActionRequestFactory.from(command);
            GlobalActionResult result = ownerActionClient.dispatch(request);
            boolean success = result != null && result.getStatus() == ActionStatus.SUCCEEDED;
            String errorCode = success ? null : ownerErrorCode(result);
            record(command, "OWNER_ACTION", success ? "SUCCEEDED" : "FAILED", started,
                    200, errorCode, ownerEvidence(request, result, false, null));
            if (!success) {
                boolean retryable = result != null && result.isRetryable();
                if (retryable) {
                    throw ApplicationFailure.newFailure(
                            "OPENAPI_OWNER_ACTION_TRANSIENT_FAILURE", "OWNER_ACTION_TRANSIENT");
                }
                throw nonRetryable("OPENAPI_OWNER_ACTION_FAILED:" + sanitize(errorCode));
            }
            return objectMapper.createObjectNode()
                    .set("payload", objectMapper.valueToTree(result)).toString();
        } catch (ApplicationFailure failure) {
            throw failure;
        } catch (OwnerActionDispatchException exception) {
            record(command, "OWNER_ACTION", "FAILED", started, exception.getExternalStatus(),
                    exception.getMessage(), ownerEvidence(request, null,
                            exception.isRetryable(), exception.getMessage()));
            if (exception.isRetryable()) {
                throw ApplicationFailure.newFailure(
                        "OPENAPI_OWNER_ACTION_TRANSPORT_FAILURE", "OWNER_ACTION_TRANSIENT");
            }
            throw nonRetryable(sanitize(exception.getMessage()));
        } catch (BizException exception) {
            record(command, "OWNER_ACTION", "FAILED", started, null,
                    String.valueOf(exception.getCode()), ownerEvidence(request, null,
                            false, exception.getMessage()));
            throw nonRetryable(sanitize(exception.getMessage()));
        } catch (Exception exception) {
            record(command, "OWNER_ACTION", "FAILED", started, null,
                    "OWNER_ACTION_DISPATCH", ownerEvidence(request, null,
                            true, exception.getMessage()));
            throw ApplicationFailure.newFailure(
                    "OPENAPI_OWNER_ACTION_DISPATCH_FAILURE", "OWNER_ACTION_TRANSIENT");
        }
    }

    @Override
    @Transactional
    public String persistExecution(String stateCommandJson) {
        JsonNode command = read(stateCommandJson);
        IntegrationExecution execution = executionMapper.selectById(command.path("executionId").asText());
        if (execution == null) {
            throw nonRetryable("OPENAPI_EXECUTION_NOT_FOUND");
        }
        ExecutionState state = ExecutionState.valueOf(command.path("state").asText());
        execution.setExecutionState(state);
        execution.setUpdatedAt(LocalDateTime.now());
        execution.setDurationMillis(command.path("durationMillis").asLong());
        if (command.has("error")) {
            execution.setErrorCode(command.path("partialFailure").asBoolean()
                    ? "ORCHESTRATION_PARTIAL_FAILURE" : "ORCHESTRATION_FAILED");
            execution.setSanitizedError(sanitize(command.path("error").asText()));
        }
        if (terminal(state)) {
            execution.setCompletedAt(LocalDateTime.now());
        }
        executionMapper.updateById(execution);
        if (terminal(state)) {
            com.triobase.common.openapi.entity.IdempotencyRecord record =
                    idempotencyMapper.selectOne(new LambdaQueryWrapper<com.triobase.common.openapi.entity.IdempotencyRecord>()
                            .eq(com.triobase.common.openapi.entity.IdempotencyRecord::getExecutionId,
                                    execution.getId()).last("LIMIT 1"));
            if (record != null) {
                record.setRecordState(state == ExecutionState.SUCCEEDED ? "SUCCEEDED" : "FAILED");
                record.setUpdatedAt(LocalDateTime.now());
                idempotencyMapper.updateById(record);
            }
            runtimeBudgetService.releaseWorkflow(execution.getTenantId(),
                    execution.getApplicationClientId(), execution.getRouteDefinitionId());
        }
        return objectMapper.createObjectNode().put("persisted", true).toString();
    }

    @Override
    @Transactional
    public String persistWait(String waitCommandJson) {
        JsonNode command = read(waitCommandJson);
        String phase = command.path("phase").asText();
        record(command, "WAIT", "RESUMED".equals(phase) ? "SUCCEEDED" : "RUNNING",
                LocalDateTime.now(), null, null,
                JsonNodeFactory.instance.objectNode().put("phase", phase));
        return objectMapper.createObjectNode().put("persisted", true).toString();
    }

    @Override
    @Transactional
    public String compensate(String compensationCommandJson) {
        JsonNode command = read(compensationCommandJson);
        JsonNode step = command.path("step");
        LocalDateTime started = LocalDateTime.now();
        try {
            JsonNode output;
            if (StringUtils.hasText(step.path("connectorVersionId").asText())) {
                output = read(invokeConnector(compensationCommandJson)).path("payload");
            } else {
                output = mappingExecutor.execute(step.path("mappingVersionId").asText(),
                        command.path("payload"));
            }
            record(command, "COMPENSATE", "COMPENSATED", started, null, null,
                    JsonNodeFactory.instance.objectNode().put("reverseOrder", true));
            return objectMapper.createObjectNode().set("payload", output).toString();
        } catch (RuntimeException exception) {
            record(command, "COMPENSATE", "FAILED", started, null,
                    "COMPENSATION_FAILED", evidence(true, exception.getMessage()));
            throw exception;
        }
    }

    private void record(JsonNode command, String stepType, String attemptState,
                        LocalDateTime started, Integer externalStatus, String errorCode,
                        JsonNode evidence) {
        String executionId = command.path("executionId").asText();
        String stepKey = command.path("step").path("key").asText("workflow");
        JsonNode action = evidence == null ? JsonNodeFactory.instance.objectNode() : evidence.path("action");
        ExecutionStepAttempt attempt = new ExecutionStepAttempt();
        attempt.setId(UlidGenerator.nextUlid());
        attempt.setExecutionId(executionId);
        attempt.setStepKey(stepKey);
        attempt.setStepType(stepType);
        attempt.setAttemptNumber(nextAttempt(executionId, stepKey));
        attempt.setAttemptState(attemptState);
        attempt.setStartedAt(started);
        attempt.setCompletedAt("RUNNING".equals(attemptState) ? null : LocalDateTime.now());
        attempt.setDurationMillis("RUNNING".equals(attemptState) ? null
                : Math.max(0, Duration.between(started, LocalDateTime.now()).toMillis()));
        attempt.setExternalStatus(externalStatus);
        attempt.setErrorCode(errorCode);
        attempt.setSanitizedError(errorCode == null ? null : sanitize(errorCode));
        attempt.setActionId(nullableText(action, "actionId"));
        attempt.setActionType(nullableText(action, "actionType"));
        attempt.setActionSource(nullableText(action, "source"));
        attempt.setActionActorType(nullableText(action, "actorType"));
        attempt.setActionActorId(nullableText(action, "actorId"));
        attempt.setActionActorName(nullableText(action, "actorName"));
        attempt.setActionTraceId(nullableText(action, "traceId"));
        attempt.setActionCorrelationId(nullableText(action, "correlationId"));
        attempt.setEvidence(redactor.payload(evidence == null
                ? JsonNodeFactory.instance.objectNode() : evidence, null));
        attempt.setCreatedAt(LocalDateTime.now());
        attemptMapper.insert(attempt);
    }

    private ObjectNode ownerEvidence(GlobalActionRequest request, GlobalActionResult result,
                                     boolean retryable, String error) {
        ObjectNode evidence = JsonNodeFactory.instance.objectNode()
                .put("retryable", retryable || (result != null && result.isRetryable()))
                .put("temporalAttempt", Activity.getExecutionContext().getInfo().getAttempt());
        if (error != null) {
            evidence.put("errorClass", error.contains("TIMEOUT") ? "TIMEOUT" : "SANITIZED");
        }
        ObjectNode action = evidence.putObject("action");
        if (request != null) {
            action.put("actionId", request.getActionId());
            action.put("actionType", request.getActionType());
            action.put("source", request.getSource() != null ? request.getSource().name() : null);
            if (request.getActor() != null) {
                action.put("actorType", request.getActor().getType() != null
                        ? request.getActor().getType().name() : null);
                action.put("actorId", request.getActor().getId());
                action.put("actorName", request.getActor().getDisplayName());
            }
            if (request.getContext() != null) {
                action.put("traceId", request.getContext().getTraceId());
                action.put("correlationId", request.getContext().getCorrelationId());
            }
            if (request.getTarget() != null) {
                action.put("ownerService", request.getTarget().getOwnerService());
                action.put("targetType", request.getTarget().getType());
                action.put("targetId", request.getTarget().getId());
            }
        }
        if (result != null) {
            action.put("ownerExecutionRef", result.getOwnerExecutionRef());
            action.put("ownerStatus", result.getStatus() != null ? result.getStatus().name() : null);
            action.put("targetStatus", result.getTargetStatus());
            action.put("targetStatusGroup", result.getTargetStatusGroup());
            action.put("refreshScopeCount", result.getRefreshScopes() == null
                    ? 0 : result.getRefreshScopes().size());
            action.put("errorCount", result.getErrors() == null ? 0 : result.getErrors().size());
        }
        return evidence;
    }

    private String ownerErrorCode(GlobalActionResult result) {
        if (result == null) {
            return "OWNER_ACTION_RESULT_MISSING";
        }
        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            ActionError error = result.getErrors().get(0);
            if (StringUtils.hasText(error.getCode())) {
                return error.getCode();
            }
        }
        if (result.getStatus() != null) {
            return "OWNER_ACTION_" + result.getStatus().name();
        }
        return StringUtils.hasText(result.getMessage()) ? result.getMessage() : "OWNER_ACTION_FAILED";
    }

    private int nextAttempt(String executionId, String stepKey) {
        ExecutionStepAttempt latest = attemptMapper.selectOne(
                new LambdaQueryWrapper<ExecutionStepAttempt>()
                        .eq(ExecutionStepAttempt::getExecutionId, executionId)
                        .eq(ExecutionStepAttempt::getStepKey, stepKey)
                        .orderByDesc(ExecutionStepAttempt::getAttemptNumber)
                        .last("LIMIT 1"));
        return latest == null ? 1 : latest.getAttemptNumber() + 1;
    }

    private String activityIdempotencyKey(JsonNode command) {
        String root = command.path("context").path(OpenApiTemporalContext.IDEMPOTENCY_KEY).asText();
        return root + ':' + command.path("step").path("key").asText()
                + ':' + command.path("phase").asText();
    }

    private ObjectNode evidence(boolean retryable, String error) {
        ObjectNode evidence = JsonNodeFactory.instance.objectNode().put("retryable", retryable)
                .put("temporalAttempt", Activity.getExecutionContext().getInfo().getAttempt());
        if (error != null) {
            evidence.put("errorClass", error.contains("TIMEOUT") ? "TIMEOUT" : "SANITIZED");
        }
        return evidence;
    }

    private String nullableText(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(field).asText();
        return StringUtils.hasText(value) ? value : null;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw nonRetryable("OPENAPI_ORCHESTRATION_JSON_CONTRACT_INVALID");
        }
    }

    private boolean terminal(ExecutionState state) {
        return state == ExecutionState.SUCCEEDED || state == ExecutionState.FAILED
                || state == ExecutionState.COMPENSATED || state == ExecutionState.CANCELLED;
    }

    private ApplicationFailure nonRetryable(String message) {
        return ApplicationFailure.newNonRetryableFailure(message, "OPENAPI_NON_RETRYABLE");
    }

    private String sanitize(String message) {
        if (message == null) {
            return "OPENAPI_ORCHESTRATION_ACTIVITY_FAILED";
        }
        String sanitized = message.replaceAll(
                "(?i)(bearer|basic)\\s+[A-Za-z0-9._~+/=-]+", "$1 ***");
        return sanitized.length() > 512 ? sanitized.substring(0, 512) : sanitized;
    }
}
