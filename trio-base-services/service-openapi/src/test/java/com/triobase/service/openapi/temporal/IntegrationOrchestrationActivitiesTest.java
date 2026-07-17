package com.triobase.service.openapi.temporal;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.entity.ExecutionStepAttempt;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionStepAttemptMapper;
import com.triobase.service.openapi.infrastructure.mapper.IdempotencyRecordMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.OrchestrationVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ReleaseSnapshotMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteVersionMapper;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import com.triobase.service.openapi.integration.http.OutboundIntegrationClient;
import com.triobase.service.openapi.integration.http.SensitiveDataRedactor;
import com.triobase.service.openapi.service.CompiledMappingExecutor;
import com.triobase.service.openapi.service.RuntimeBudgetService;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.testing.TestActivityEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationOrchestrationActivitiesTest {

    @Mock private ReleaseSnapshotMapper releaseMapper;
    @Mock private RouteVersionMapper routeMapper;
    @Mock private OrchestrationVersionMapper orchestrationMapper;
    @Mock private ConnectorVersionMapper connectorMapper;
    @Mock private IntegrationExecutionMapper executionMapper;
    @Mock private IdempotencyRecordMapper idempotencyMapper;
    @Mock private ExecutionStepAttemptMapper attemptMapper;
    @Mock private CompiledMappingExecutor mappingExecutor;
    @Mock private CredentialProvider credentialProvider;
    @Mock private OutboundIntegrationClient outboundClient;
    @Mock private RuntimeBudgetService budgetService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TestActivityEnvironment environment;
    private IntegrationOrchestrationActivities activities;

    @BeforeEach
    void setUp() {
        IntegrationOrchestrationActivitiesImpl implementation =
                new IntegrationOrchestrationActivitiesImpl(
                        releaseMapper, routeMapper, orchestrationMapper, connectorMapper,
                        executionMapper, idempotencyMapper, attemptMapper, mappingExecutor,
                        credentialProvider, outboundClient, new SensitiveDataRedactor(),
                        budgetService, objectMapper);
        environment = TestActivityEnvironment.newInstance();
        environment.registerActivitiesImplementations(implementation);
        activities = environment.newActivityStub(
                IntegrationOrchestrationActivities.class,
                ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(5))
                        .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                        .build());
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @Test
    void connectorActivityPropagatesStableBusinessIdempotencyAndSanitizedEvidence() throws Exception {
        ConnectorVersion connector = new ConnectorVersion();
        connector.setId("connector-v1");
        connector.setLifecycleState(VersionLifecycleState.PUBLISHED);
        connector.setAuthenticationType(AuthenticationType.NONE);
        when(connectorMapper.selectById("connector-v1")).thenReturn(connector);
        when(attemptMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(attemptMapper.insert(any(ExecutionStepAttempt.class))).thenReturn(1);
        when(outboundClient.execute(any())).thenReturn(new OutboundIntegrationClient.OutboundResponse(
                200, Map.of(), "{\"partnerId\":\"p1\"}".getBytes(StandardCharsets.UTF_8), 12));

        String result = activities.invokeConnector("""
                {"executionId":"execution-1","phase":"EXECUTE",
                 "step":{"key":"create-order","type":"INVOKE","connectorVersionId":"connector-v1"},
                 "payload":{"amount":100},
                 "context":{"traceId":"trace-1","idempotencyKey":"request-1"}}
                """);

        assertThat(objectMapper.readTree(result).at("/payload/partnerId").asText()).isEqualTo("p1");
        ArgumentCaptor<OutboundIntegrationClient.OutboundRequest> requestCaptor =
                ArgumentCaptor.forClass(OutboundIntegrationClient.OutboundRequest.class);
        verify(outboundClient).execute(requestCaptor.capture());
        assertThat(requestCaptor.getValue().headers().get("Idempotency-Key"))
                .containsExactly("request-1:create-order:EXECUTE");
        assertThat(requestCaptor.getValue().headers().get("X-B3-TraceId"))
                .containsExactly("trace-1");
        ArgumentCaptor<ExecutionStepAttempt> attemptCaptor =
                ArgumentCaptor.forClass(ExecutionStepAttempt.class);
        verify(attemptMapper).insert(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getEvidence().has("responseBytes")).isTrue();
        assertThat(attemptCaptor.getValue().getEvidence().toString()).doesNotContain("partnerId");
    }
}
