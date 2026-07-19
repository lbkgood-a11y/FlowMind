package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.common.temporal.interceptor.TraceContextPropagator;
import com.triobase.service.openapi.domain.entity.IdempotencyRecord;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.CompiledRouteRelease;
import com.triobase.service.openapi.dto.OrchestrationExecutionResponse;
import com.triobase.service.openapi.dto.RuntimeAdmissionContext;
import com.triobase.service.openapi.action.OpenApiActionMetadata;
import com.triobase.service.openapi.infrastructure.mapper.IdempotencyRecordMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteVersionMapper;
import com.triobase.service.openapi.temporal.IntegrationOrchestrationWorkflow;
import com.triobase.service.openapi.temporal.OpenApiContextPropagator;
import com.triobase.service.openapi.temporal.OpenApiTemporalContext;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class OrchestrationRuntimeService {

    private final ReleaseManagementService releases;
    private final RouteVersionMapper routeMapper;
    private final IntegrationExecutionMapper executionMapper;
    private final IdempotencyRecordMapper idempotencyMapper;
    private final RuntimeBudgetService runtimeBudgetService;
    private final ProductSubscriptionService subscriptions;
    private final WorkflowClient workflowClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String taskQueue;

    public OrchestrationRuntimeService(
            ReleaseManagementService releases,
            RouteVersionMapper routeMapper,
            IntegrationExecutionMapper executionMapper,
            IdempotencyRecordMapper idempotencyMapper,
            RuntimeBudgetService runtimeBudgetService,
            ProductSubscriptionService subscriptions,
            WorkflowClient workflowClient,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${triobase.openapi.temporal.task-queue:${spring.application.name}}") String taskQueue) {
        this.releases = releases;
        this.routeMapper = routeMapper;
        this.executionMapper = executionMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.runtimeBudgetService = runtimeBudgetService;
        this.subscriptions = subscriptions;
        this.workflowClient = workflowClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.taskQueue = taskQueue;
    }

    public OrchestrationExecutionResponse start(
            String routeKey, Environment environment, RuntimeAdmissionContext admission,
            String operation, String idempotencyKey, JsonNode payload) {
        requireAdmission(admission, environment, idempotencyKey);
        CompiledRouteRelease release = releases.resolveActive(admission.tenantId(), routeKey, environment);
        requireReleaseAllowed(release, admission);
        RouteVersion route = routeMapper.selectById(release.routeVersionId());
        if (route == null || route.getExecutionMode() != ExecutionMode.ORCHESTRATED
                || !StringUtils.hasText(route.getOrchestrationVersionId())) {
            throw new BizException(40960, "OPENAPI_ROUTE_NOT_ORCHESTRATED");
        }
        subscriptions.requireRuntimeAccess(admission.applicationClientId(), admission.subscriptionId(),
                routeKey, operation, LocalDateTime.now());
        String requestHash = hash(payload == null ? objectMapper.createObjectNode() : payload);
        IdempotencyRecord existing = findIdempotency(
                release, environment, admission.applicationClientId(), idempotencyKey);
        if (existing != null) {
            return attachExisting(existing, requestHash);
        }

        runtimeBudgetService.reserveWorkflow(release.tenantId(), admission.applicationClientId(),
                release.routeId(), Math.max(1, admission.maxActiveWorkflows()));
        Reservation reservation;
        try {
            reservation = transactionTemplate.execute(status -> reserve(
                    release, environment, admission.applicationClientId(), idempotencyKey, requestHash));
        } catch (DuplicateKeyException duplicate) {
            runtimeBudgetService.releaseWorkflow(
                    release.tenantId(), admission.applicationClientId(), release.routeId());
            IdempotencyRecord raced = findIdempotency(
                    release, environment, admission.applicationClientId(), idempotencyKey);
            if (raced == null) {
                throw duplicate;
            }
            return attachExisting(raced, requestHash);
        } catch (RuntimeException failure) {
            runtimeBudgetService.releaseWorkflow(
                    release.tenantId(), admission.applicationClientId(), release.routeId());
            throw failure;
        }

        Map<String, String> context = OpenApiTemporalContext.of(
                TraceUtil.getTraceId(), release.tenantId(), admission.applicationClientId(),
                caller(admission.applicationClientId()), release.releaseId(), idempotencyKey);
        String commandJson = command(reservation.execution().getId(), release.releaseId(), payload, context);
        IntegrationOrchestrationWorkflow workflow = workflowClient.newWorkflowStub(
                IntegrationOrchestrationWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(reservation.execution().getWorkflowId())
                        .setTaskQueue(taskQueue)
                        .setWorkflowExecutionTimeout(Duration.ofDays(30))
                        .setWorkflowRunTimeout(Duration.ofDays(30))
                        .setWorkflowTaskTimeout(Duration.ofSeconds(10))
                        .setWorkflowIdReusePolicy(
                                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                        .setContextPropagators(List.of(
                                new TraceContextPropagator(), new OpenApiContextPropagator()))
                        .build());
        OpenApiTemporalContext.set(context);
        try {
            WorkflowExecution started = WorkflowClient.start(workflow::run, commandJson);
            transactionTemplate.executeWithoutResult(status -> markStarted(
                    reservation.execution().getId(), reservation.idempotency().getId(), started.getRunId()));
            return response(executionMapper.selectById(reservation.execution().getId()), false,
                    objectMapper.createObjectNode().put("accepted", true));
        } catch (WorkflowExecutionAlreadyStarted duplicate) {
            transactionTemplate.executeWithoutResult(status -> markStarted(
                    reservation.execution().getId(), reservation.idempotency().getId(),
                    duplicate.getExecution().getRunId()));
            return response(executionMapper.selectById(reservation.execution().getId()), true,
                    objectMapper.createObjectNode().put("attached", true));
        } catch (RuntimeException failure) {
            transactionTemplate.executeWithoutResult(status -> markStartFailed(reservation, failure));
            runtimeBudgetService.releaseWorkflow(
                    release.tenantId(), admission.applicationClientId(), release.routeId());
            throw new BizException(50360, "OPENAPI_TEMPORAL_START_FAILED");
        } finally {
            OpenApiTemporalContext.clear();
        }
    }

    public OrchestrationExecutionResponse status(String executionId, String applicationClientId) {
        IntegrationExecution execution = requireExecution(executionId, applicationClientId);
        JsonNode details = query(execution, true);
        return response(execution, true, details);
    }

    public OrchestrationExecutionResponse result(String executionId, String applicationClientId) {
        IntegrationExecution execution = requireExecution(executionId, applicationClientId);
        JsonNode details = query(execution, false);
        return response(execution, true, details);
    }

    public OrchestrationExecutionResponse cancel(
            String executionId, String applicationClientId, String reason) {
        IntegrationExecution execution = requireExecution(executionId, applicationClientId);
        if (terminal(execution.getExecutionState())) {
            return response(execution, true,
                    objectMapper.createObjectNode().put("alreadyTerminal", true));
        }
        workflowClient.newWorkflowStub(
                IntegrationOrchestrationWorkflow.class, execution.getWorkflowId()).requestCancel(reason);
        return response(execution, true,
                objectMapper.createObjectNode().put("cancelRequested", true));
    }

    public void signal(String executionId, String signalJson) {
        IntegrationExecution execution = requireExecution(executionId, null);
        workflowClient.newWorkflowStub(
                IntegrationOrchestrationWorkflow.class, execution.getWorkflowId()).receiveSignal(signalJson);
    }

    private Reservation reserve(CompiledRouteRelease release, Environment environment,
                                String applicationClientId, String idempotencyKey, String requestHash) {
        LocalDateTime now = LocalDateTime.now();
        String workflowId = stableWorkflowId(
                release.tenantId(), environment, release.routeKey(), release.releaseId(), idempotencyKey);
        IntegrationExecution execution = new IntegrationExecution();
        execution.setId(UlidGenerator.nextUlid());
        execution.setTenantId(release.tenantId());
        execution.setEnvironment(environment);
        execution.setApplicationClientId(applicationClientId);
        execution.setRouteDefinitionId(release.routeId());
        execution.setReleaseSnapshotId(release.releaseId());
        execution.setExecutionMode(ExecutionMode.ORCHESTRATED);
        execution.setExecutionState(ExecutionState.ACCEPTED);
        execution.setWorkflowId(workflowId);
        execution.setIdempotencyKey(idempotencyKey);
        execution.setTraceId(TraceUtil.getTraceId());
        execution.setCallerId(caller(applicationClientId));
        OpenApiActionMetadata.apply(execution);
        execution.setStartedAt(now);
        execution.setDiagnosticEnabled(false);
        execution.setRetentionUntil(now.plusDays(180));
        execution.setRowVersion(0L);
        execution.setCreatedAt(now);
        execution.setUpdatedAt(now);
        executionMapper.insert(execution);

        IdempotencyRecord idempotency = new IdempotencyRecord();
        idempotency.setId(UlidGenerator.nextUlid());
        idempotency.setTenantId(release.tenantId());
        idempotency.setEnvironment(environment);
        idempotency.setApplicationClientId(applicationClientId);
        idempotency.setRouteDefinitionId(release.routeId());
        idempotency.setReleaseSnapshotId(release.releaseId());
        idempotency.setIdempotencyKey(idempotencyKey);
        idempotency.setRequestHash(requestHash);
        idempotency.setExecutionId(execution.getId());
        idempotency.setRecordState("RESERVED");
        idempotency.setResponseReference(workflowId);
        idempotency.setExpiresAt(now.plusDays(30));
        idempotency.setCreatedAt(now);
        idempotency.setUpdatedAt(now);
        idempotencyMapper.insert(idempotency);
        return new Reservation(execution, idempotency);
    }

    private void markStarted(String executionId, String idempotencyId, String runId) {
        IntegrationExecution execution = executionMapper.selectById(executionId);
        execution.setWorkflowRunId(runId);
        execution.setExecutionState(ExecutionState.RUNNING);
        execution.setUpdatedAt(LocalDateTime.now());
        executionMapper.updateById(execution);
        IdempotencyRecord record = idempotencyMapper.selectById(idempotencyId);
        record.setRecordState("RUNNING");
        record.setUpdatedAt(LocalDateTime.now());
        idempotencyMapper.updateById(record);
    }

    private void markStartFailed(Reservation reservation, RuntimeException failure) {
        IntegrationExecution execution = reservation.execution();
        execution.setExecutionState(ExecutionState.FAILED);
        execution.setCompletedAt(LocalDateTime.now());
        execution.setErrorCode("TEMPORAL_START_FAILED");
        execution.setSanitizedError("OPENAPI_TEMPORAL_START_FAILED");
        execution.setUpdatedAt(LocalDateTime.now());
        executionMapper.updateById(execution);
        IdempotencyRecord record = reservation.idempotency();
        record.setRecordState("FAILED");
        record.setUpdatedAt(LocalDateTime.now());
        idempotencyMapper.updateById(record);
    }

    private IdempotencyRecord findIdempotency(
            CompiledRouteRelease release, Environment environment,
            String applicationClientId, String idempotencyKey) {
        return idempotencyMapper.selectOne(new LambdaQueryWrapper<IdempotencyRecord>()
                .eq(IdempotencyRecord::getTenantId, release.tenantId())
                .eq(IdempotencyRecord::getEnvironment, environment)
                .eq(IdempotencyRecord::getApplicationClientId, applicationClientId)
                .eq(IdempotencyRecord::getRouteDefinitionId, release.routeId())
                .eq(IdempotencyRecord::getReleaseSnapshotId, release.releaseId())
                .eq(IdempotencyRecord::getIdempotencyKey, idempotencyKey));
    }

    private OrchestrationExecutionResponse attachExisting(
            IdempotencyRecord record, String requestHash) {
        if (!requestHash.equals(record.getRequestHash())) {
            throw new BizException(40961, "OPENAPI_IDEMPOTENCY_KEY_PAYLOAD_MISMATCH");
        }
        IntegrationExecution execution = executionMapper.selectById(record.getExecutionId());
        if (execution == null) {
            throw new BizException(40962, "OPENAPI_IDEMPOTENCY_EXECUTION_MISSING");
        }
        return response(execution, true,
                objectMapper.createObjectNode().put("attached", true));
    }

    private IntegrationExecution requireExecution(String executionId, String applicationClientId) {
        IntegrationExecution execution = executionMapper.selectById(executionId);
        String tenantId = SecurityContextHolder.getTenantId();
        if (execution == null || (tenantId != null && !tenantId.equals(execution.getTenantId()))
                || (StringUtils.hasText(applicationClientId)
                && !applicationClientId.equals(execution.getApplicationClientId()))) {
            throw new BizException(40460, "OPENAPI_EXECUTION_NOT_FOUND");
        }
        return execution;
    }

    private JsonNode query(IntegrationExecution execution, boolean statusOnly) {
        try {
            IntegrationOrchestrationWorkflow workflow = workflowClient.newWorkflowStub(
                    IntegrationOrchestrationWorkflow.class, execution.getWorkflowId());
            return objectMapper.readTree(statusOnly ? workflow.status() : workflow.result());
        } catch (Exception unavailable) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("executionId", execution.getId());
            fallback.put("state", execution.getExecutionState().name());
            fallback.put("workflowQueryAvailable", false);
            if (execution.getSanitizedError() != null) {
                fallback.put("error", execution.getSanitizedError());
            }
            return fallback;
        }
    }

    private OrchestrationExecutionResponse response(
            IntegrationExecution execution, boolean attached, JsonNode details) {
        return new OrchestrationExecutionResponse(
                execution.getId(), execution.getWorkflowId(), execution.getWorkflowRunId(),
                execution.getExecutionState(), attached, execution.getTraceId(), details);
    }

    private String command(String executionId, String releaseId, JsonNode payload,
                           Map<String, String> context) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("executionId", executionId);
        command.put("releaseId", releaseId);
        command.set("payload", payload == null ? objectMapper.createObjectNode() : payload);
        command.set("context", objectMapper.valueToTree(context));
        return command.toString();
    }

    private void requireAdmission(RuntimeAdmissionContext admission, Environment environment,
                                  String idempotencyKey) {
        if (admission == null || !StringUtils.hasText(admission.applicationClientId())
                || !StringUtils.hasText(admission.subscriptionId()) || admission.environment() != environment) {
            throw new BizException(40130, "OPENAPI_RUNTIME_ADMISSION_CONTEXT_REQUIRED");
        }
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 256) {
            throw new BizException(40060, "OPENAPI_IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    private void requireReleaseAllowed(CompiledRouteRelease release, RuntimeAdmissionContext admission) {
        if (release == null || !StringUtils.hasText(release.tenantId())
                || !release.tenantId().equals(admission.tenantId())) {
            throw new BizException(40380, "OPENAPI_SUBSCRIPTION_ACCESS_DENIED");
        }
    }

    private String stableWorkflowId(String tenantId, Environment environment, String routeKey,
                                    String releaseId, String idempotencyKey) {
        return "openapi-" + hashText((tenantId == null ? "__PLATFORM__" : tenantId)
                + '|' + environment + '|' + routeKey + '|' + releaseId + '|' + idempotencyKey);
    }

    private String hash(JsonNode node) {
        try {
            return hashText(objectMapper.writeValueAsString(node));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash orchestration request", exception);
        }
    }

    private String hashText(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash workflow identifier", exception);
        }
    }

    private String caller(String fallback) {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : fallback;
    }

    private boolean terminal(ExecutionState state) {
        return state == ExecutionState.SUCCEEDED || state == ExecutionState.FAILED
                || state == ExecutionState.COMPENSATED || state == ExecutionState.CANCELLED;
    }

    private record Reservation(IntegrationExecution execution, IdempotencyRecord idempotency) {
    }
}
