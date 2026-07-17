package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.ExecutionStepAttempt;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionStepAttemptMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionQueryServiceTest {

    @Mock private IntegrationExecutionMapper executionMapper;
    @Mock private ExecutionStepAttemptMapper attemptMapper;
    private ExecutionQueryService service;

    @BeforeEach
    void setUp() {
        service = new ExecutionQueryService(executionMapper, attemptMapper);
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "operator", "Operator", "tenant-a", List.of(), List.of(), 1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void searchesTenantScopedExecutionsWithPaginationAndRetentionMetadata() {
        Page<IntegrationExecution> page = new Page<>(1, 20);
        page.setRecords(List.of(execution("tenant-a")));
        page.setTotal(1);
        when(executionMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(page);

        var result = service.search(1, 20, "client-1", null, Environment.PROD,
                ExecutionState.SUCCEEDED, "trace-1", null, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().getFirst().retentionUntil()).isNotNull();
        assertThat(result.getRecords().getFirst().applicationClientId()).isEqualTo("client-1");
    }

    @Test
    void returnsSanitizedAttemptEvidenceAndHidesCrossTenantExecution() {
        IntegrationExecution execution = execution("tenant-a");
        when(executionMapper.selectById("execution-1")).thenReturn(execution);
        ExecutionStepAttempt attempt = new ExecutionStepAttempt();
        attempt.setStepKey("invoke");
        attempt.setSanitizedError("PARTNER_TIMEOUT");
        when(attemptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(attempt));
        assertThat(service.get("execution-1").attempts()).hasSize(1);

        execution.setTenantId("tenant-b");
        assertThatThrownBy(() -> service.get("execution-1"))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_EXECUTION_NOT_FOUND");
    }

    private IntegrationExecution execution(String tenantId) {
        IntegrationExecution execution = new IntegrationExecution();
        execution.setId("execution-1");
        execution.setTenantId(tenantId);
        execution.setEnvironment(Environment.PROD);
        execution.setApplicationClientId("client-1");
        execution.setRouteDefinitionId("route-1");
        execution.setReleaseSnapshotId("release-1");
        execution.setExecutionMode(ExecutionMode.ORCHESTRATED);
        execution.setExecutionState(ExecutionState.SUCCEEDED);
        execution.setTraceId("trace-1");
        execution.setStartedAt(LocalDateTime.now().minusSeconds(1));
        execution.setRetentionUntil(LocalDateTime.now().plusDays(180));
        return execution;
    }
}
