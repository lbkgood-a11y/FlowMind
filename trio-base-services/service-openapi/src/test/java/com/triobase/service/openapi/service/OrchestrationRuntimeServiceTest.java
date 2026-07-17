package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.IdempotencyRecord;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.CompiledRouteRelease;
import com.triobase.service.openapi.infrastructure.mapper.IdempotencyRecordMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteVersionMapper;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestrationRuntimeServiceTest {

    @Mock private ReleaseManagementService releases;
    @Mock private RouteVersionMapper routeMapper;
    @Mock private IntegrationExecutionMapper executionMapper;
    @Mock private IdempotencyRecordMapper idempotencyMapper;
    @Mock private RuntimeBudgetService budgets;
    @Mock private WorkflowClient workflowClient;
    @Mock private PlatformTransactionManager transactionManager;
    private OrchestrationRuntimeService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new OrchestrationRuntimeService(releases, routeMapper, executionMapper,
                idempotencyMapper, budgets, workflowClient, new ObjectMapper(),
                transactionManager, "service-openapi");
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "APP:client-a", "client-a", "tenant-a", List.of("EXTERNAL_APPLICATION"),
                List.of(), 1L, 1L, 1L));
        when(releases.resolveActive("orders.submit", Environment.PROD)).thenReturn(release());
        when(routeMapper.selectById("route-version-1")).thenReturn(route());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void duplicateIdempotencyKeyAttachesToExistingExecutionWithoutNewWorkflow() throws Exception {
        IdempotencyRecord record = idempotency("existing-hash");
        IntegrationExecution execution = execution();
        when(idempotencyMapper.selectOne(any(Wrapper.class))).thenReturn(record);
        when(executionMapper.selectById("execution-1")).thenReturn(execution);
        ObjectMapper mapper = new ObjectMapper();
        record.setRequestHash(hash(mapper, mapper.readTree("{\"amount\":1}")));

        var response = service.start("orders.submit", Environment.PROD, "client-a",
                "subscription-1", "idem-1", 10, mapper.readTree("{\"amount\":1}"));

        assertThat(response.attachedToExisting()).isTrue();
        assertThat(response.executionId()).isEqualTo("execution-1");
        verify(budgets, never()).reserveWorkflow(any(), any(), any(), anyLong());
        verify(workflowClient, never()).newWorkflowStub(any(Class.class), any(String.class));
    }

    @Test
    void duplicateKeyWithDifferentPayloadIsRejected() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        when(idempotencyMapper.selectOne(any(Wrapper.class))).thenReturn(idempotency("different"));

        assertThatThrownBy(() -> service.start("orders.submit", Environment.PROD, "client-a",
                "subscription-1", "idem-1", 10, mapper.readTree("{\"amount\":2}")))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_IDEMPOTENCY_KEY_PAYLOAD_MISMATCH");
    }

    private CompiledRouteRelease release() {
        return new CompiledRouteRelease("tenant-a", Environment.PROD, "orders.submit",
                "route-1", "route-version-1", "release-1", 1L, "hash",
                new ObjectMapper().createObjectNode());
    }

    private RouteVersion route() {
        RouteVersion route = new RouteVersion();
        route.setExecutionMode(ExecutionMode.ORCHESTRATED);
        route.setOrchestrationVersionId("orchestration-v1");
        return route;
    }

    private IdempotencyRecord idempotency(String requestHash) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setId("idempotency-1");
        record.setExecutionId("execution-1");
        record.setRequestHash(requestHash);
        return record;
    }

    private IntegrationExecution execution() {
        IntegrationExecution execution = new IntegrationExecution();
        execution.setId("execution-1");
        execution.setTenantId("tenant-a");
        execution.setApplicationClientId("client-a");
        execution.setWorkflowId("openapi-workflow-1");
        execution.setWorkflowRunId("run-1");
        execution.setExecutionState(ExecutionState.RUNNING);
        execution.setTraceId("trace-1");
        return execution;
    }

    private String hash(ObjectMapper mapper, com.fasterxml.jackson.databind.JsonNode node) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsString(node).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
