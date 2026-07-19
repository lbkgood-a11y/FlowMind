package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.entity.ExecutionStepAttempt;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.ConnectorOperationClass;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.CompiledRouteRelease;
import com.triobase.service.openapi.dto.RuntimeAdmissionContext;
import com.triobase.service.openapi.dto.SyncInvocationResponse;
import com.triobase.service.openapi.action.OpenApiActionMetadata;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionStepAttemptMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteVersionMapper;
import com.triobase.service.openapi.integration.credential.CredentialMaterial;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import com.triobase.service.openapi.integration.http.OutboundIntegrationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SynchronousInvocationService {

    private final ReleaseManagementService releases;
    private final RouteVersionMapper routeMapper;
    private final ConnectorVersionMapper connectorMapper;
    private final CompiledMappingExecutor mappingExecutor;
    private final CredentialProvider credentialProvider;
    private final OutboundIntegrationClient outbound;
    private final IntegrationExecutionMapper executionMapper;
    private final ExecutionStepAttemptMapper attemptMapper;
    private final RuntimeBudgetService budgets;
    private final ProductSubscriptionService subscriptions;
    private final IntegrationAuditService auditService;
    private final ObjectMapper objectMapper;

    public SyncInvocationResponse invoke(
            String routeKey,
            Environment environment,
            RuntimeAdmissionContext admission,
            String operation,
            JsonNode canonicalRequest) {
        requireAdmission(admission, environment);
        CompiledRouteRelease release = releases.resolveActive(admission.tenantId(), routeKey, environment);
        requireReleaseAllowed(release, admission);
        RouteVersion route = routeMapper.selectById(release.routeVersionId());
        ConnectorVersion connector = route == null ? null : connectorMapper.selectById(route.getConnectorVersionId());
        requireEligible(route, connector);
        subscriptions.requireRuntimeAccess(admission.applicationClientId(), admission.subscriptionId(),
                routeKey, operation, LocalDateTime.now());

        IntegrationExecution execution = start(release, environment, admission.applicationClientId());
        long started = System.nanoTime();
        try (RuntimeBudgetService.BudgetLease ignored = budgets.acquireRequest(
                release.tenantId(),
                admission.applicationClientId(),
                routeKey,
                Math.max(1L, admission.maxConcurrency()))) {
            JsonNode externalRequest = mappingExecutor.execute(
                    route.getRequestMappingVersionId(),
                    canonicalRequest == null ? objectMapper.createObjectNode() : canonicalRequest);
            CredentialMaterial credential = connector.getAuthenticationType() == AuthenticationType.NONE
                    ? null : credentialProvider.resolve(connector.getSecretReference());
            LocalDateTime callStarted = LocalDateTime.now();
            OutboundIntegrationClient.OutboundResponse partner = outbound.execute(
                    new OutboundIntegrationClient.OutboundRequest(
                            connector, externalRequest, Map.of(), credential));
            recordAttempt(execution.getId(), partner, callStarted);
            if (partner.status() < 200 || partner.status() >= 300) {
                throw new BizException(50240, "OPENAPI_PARTNER_HTTP_ERROR:" + partner.status());
            }
            JsonNode externalResponse = parse(partner.body());
            JsonNode canonicalResponse = mappingExecutor.execute(
                    route.getResponseMappingVersionId(), externalResponse);
            long duration = Duration.ofNanos(System.nanoTime() - started).toMillis();
            succeed(execution, duration);
            auditService.success("SYNCHRONOUS_INVOCATION_SUCCEEDED", "EXECUTION", execution.getId(),
                    JsonNodeFactory.instance.objectNode()
                            .put("routeKey", routeKey)
                            .put("subscriptionId", admission.subscriptionId())
                            .put("policyVersion", admission.policyVersion())
                            .put("partnerStatus", partner.status()));
            return new SyncInvocationResponse(
                    execution.getId(), partner.status(), canonicalResponse, TraceUtil.getTraceId(), duration);
        } catch (BizException exception) {
            fail(execution, exception, System.nanoTime() - started);
            throw exception;
        } catch (Exception exception) {
            BizException normalized = new BizException(50241, "OPENAPI_SYNCHRONOUS_INVOCATION_FAILED");
            fail(execution, normalized, System.nanoTime() - started);
            throw normalized;
        }
    }

    private void requireAdmission(RuntimeAdmissionContext admission, Environment environment) {
        if (admission == null || !StringUtils.hasText(admission.applicationClientId())
                || !StringUtils.hasText(admission.subscriptionId())) {
            throw new BizException(40130, "OPENAPI_RUNTIME_ADMISSION_CONTEXT_REQUIRED");
        }
        if (admission.environment() != environment) {
            throw new BizException(40130, "OPENAPI_RUNTIME_ADMISSION_CONTEXT_REQUIRED");
        }
    }

    private void requireReleaseAllowed(CompiledRouteRelease release, RuntimeAdmissionContext admission) {
        if (release == null || !StringUtils.hasText(release.tenantId())
                || !release.tenantId().equals(admission.tenantId())) {
            throw new BizException(40380, "OPENAPI_SUBSCRIPTION_ACCESS_DENIED");
        }
    }

    private void requireEligible(RouteVersion route, ConnectorVersion connector) {
        if (route == null
                || route.getExecutionMode() != ExecutionMode.SYNCHRONOUS
                || StringUtils.hasText(route.getOrchestrationVersionId())
                || !StringUtils.hasText(route.getRequestMappingVersionId())
                || !StringUtils.hasText(route.getResponseMappingVersionId())
                || connector == null
                || connector.getOperationClass() != ConnectorOperationClass.READ_ONLY
                || connector.getTimeoutMillis() == null
                || connector.getTimeoutMillis() >= 500) {
            throw new BizException(40930, "OPENAPI_ROUTE_NOT_SYNCHRONOUSLY_ELIGIBLE");
        }
    }

    private IntegrationExecution start(CompiledRouteRelease release, Environment environment, String clientId) {
        LocalDateTime now = LocalDateTime.now();
        IntegrationExecution execution = new IntegrationExecution();
        execution.setId(UlidGenerator.nextUlid());
        execution.setTenantId(release.tenantId());
        execution.setEnvironment(environment);
        execution.setApplicationClientId(clientId);
        execution.setRouteDefinitionId(release.routeId());
        execution.setReleaseSnapshotId(release.releaseId());
        execution.setExecutionMode(ExecutionMode.SYNCHRONOUS);
        execution.setExecutionState(ExecutionState.RUNNING);
        execution.setTraceId(TraceUtil.getTraceId());
        execution.setCallerId(clientId);
        OpenApiActionMetadata.apply(execution);
        execution.setStartedAt(now);
        execution.setDiagnosticEnabled(false);
        execution.setRetentionUntil(now.plusDays(180));
        execution.setRowVersion(0L);
        execution.setCreatedAt(now);
        execution.setUpdatedAt(now);
        executionMapper.insert(execution);
        return execution;
    }

    private void recordAttempt(
            String executionId,
            OutboundIntegrationClient.OutboundResponse response,
            LocalDateTime started) {
        LocalDateTime now = LocalDateTime.now();
        ExecutionStepAttempt attempt = new ExecutionStepAttempt();
        attempt.setId(UlidGenerator.nextUlid());
        attempt.setExecutionId(executionId);
        attempt.setStepKey("connector");
        attempt.setStepType("HTTP_INVOKE");
        attempt.setAttemptNumber(1);
        attempt.setAttemptState(response.status() >= 200 && response.status() < 300 ? "SUCCEEDED" : "FAILED");
        attempt.setStartedAt(started);
        attempt.setCompletedAt(now);
        attempt.setDurationMillis(response.durationMillis());
        attempt.setExternalStatus(response.status());
        attempt.setEvidence(JsonNodeFactory.instance.objectNode().put("responseBytes", response.body().length));
        OpenApiActionMetadata.apply(attempt);
        attempt.setCreatedAt(now);
        attemptMapper.insert(attempt);
    }

    private JsonNode parse(byte[] body) {
        try {
            return body == null || body.length == 0
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new BizException(50242, "OPENAPI_PARTNER_RESPONSE_NOT_JSON");
        }
    }

    private void succeed(IntegrationExecution execution, long duration) {
        execution.setExecutionState(ExecutionState.SUCCEEDED);
        execution.setCompletedAt(LocalDateTime.now());
        execution.setDurationMillis(duration);
        execution.setUpdatedAt(LocalDateTime.now());
        executionMapper.updateById(execution);
    }

    private void fail(IntegrationExecution execution, BizException error, long nanos) {
        execution.setExecutionState(ExecutionState.FAILED);
        execution.setCompletedAt(LocalDateTime.now());
        execution.setDurationMillis(Duration.ofNanos(nanos).toMillis());
        execution.setErrorCode(String.valueOf(error.getCode()));
        execution.setSanitizedError(sanitize(error.getMessage()));
        execution.setUpdatedAt(LocalDateTime.now());
        executionMapper.updateById(execution);
        auditService.failure("SYNCHRONOUS_INVOCATION_FAILED", "EXECUTION",
                execution.getId(), execution.getSanitizedError(), JsonNodeFactory.instance.objectNode());
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll(
                "(?i)(bearer|basic)\\s+[A-Za-z0-9._~+/=-]+", "$1 ***");
        return sanitized.length() > 512 ? sanitized.substring(0, 512) : sanitized;
    }
}
