package com.triobase.service.openapi.temporal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.entity.ExecutionStepAttempt;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.entity.OrchestrationVersion;
import com.triobase.service.openapi.domain.entity.ReleaseSnapshot;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionStepAttemptMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.IdempotencyRecordMapper;
import com.triobase.service.openapi.infrastructure.mapper.OrchestrationVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ReleaseSnapshotMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteVersionMapper;
import com.triobase.service.openapi.integration.credential.CredentialMaterial;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import com.triobase.service.openapi.integration.http.OutboundIntegrationClient;
import com.triobase.service.openapi.integration.http.SensitiveDataRedactor;
import com.triobase.service.openapi.service.CompiledMappingExecutor;
import com.triobase.service.openapi.service.RuntimeBudgetService;
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

@Component
@ActivityImpl(taskQueues = "service-openapi")
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
            com.triobase.service.openapi.domain.entity.IdempotencyRecord record =
                    idempotencyMapper.selectOne(new LambdaQueryWrapper<com.triobase.service.openapi.domain.entity.IdempotencyRecord>()
                            .eq(com.triobase.service.openapi.domain.entity.IdempotencyRecord::getExecutionId,
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
        attempt.setEvidence(redactor.payload(evidence == null
                ? JsonNodeFactory.instance.objectNode() : evidence, null));
        attempt.setCreatedAt(LocalDateTime.now());
        attemptMapper.insert(attempt);
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
