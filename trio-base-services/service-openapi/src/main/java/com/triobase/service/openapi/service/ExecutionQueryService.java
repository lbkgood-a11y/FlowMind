package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.openapi.domain.entity.ExecutionStepAttempt;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.ExecutionDetailResponse;
import com.triobase.service.openapi.dto.ExecutionSummaryResponse;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionStepAttemptMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExecutionQueryService {

    private final IntegrationExecutionMapper executionMapper;
    private final ExecutionStepAttemptMapper attemptMapper;

    public PageResult<ExecutionSummaryResponse> search(
            int page, int size, String applicationClientId, String routeDefinitionId,
            Environment environment, ExecutionState state, String traceId,
            LocalDateTime startedFrom, LocalDateTime startedUntil) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        String tenantId = SecurityContextHolder.getTenantId();
        LambdaQueryWrapper<IntegrationExecution> query = new LambdaQueryWrapper<IntegrationExecution>()
                .eq(tenantId != null, IntegrationExecution::getTenantId, tenantId)
                .eq(StringUtils.hasText(applicationClientId),
                        IntegrationExecution::getApplicationClientId, applicationClientId)
                .eq(StringUtils.hasText(routeDefinitionId),
                        IntegrationExecution::getRouteDefinitionId, routeDefinitionId)
                .eq(environment != null, IntegrationExecution::getEnvironment, environment)
                .eq(state != null, IntegrationExecution::getExecutionState, state)
                .eq(StringUtils.hasText(traceId), IntegrationExecution::getTraceId, traceId)
                .ge(startedFrom != null, IntegrationExecution::getStartedAt, startedFrom)
                .lt(startedUntil != null, IntegrationExecution::getStartedAt, startedUntil)
                .orderByDesc(IntegrationExecution::getStartedAt);
        Page<IntegrationExecution> result = executionMapper.selectPage(new Page<>(safePage, safeSize), query);
        return PageResult.of(result.getRecords().stream().map(this::summary).toList(),
                result.getTotal(), safePage, safeSize);
    }

    public ExecutionDetailResponse get(String executionId) {
        IntegrationExecution execution = executionMapper.selectById(executionId);
        String tenantId = SecurityContextHolder.getTenantId();
        if (execution == null || (tenantId != null && !tenantId.equals(execution.getTenantId()))) {
            throw new BizException(40460, "OPENAPI_EXECUTION_NOT_FOUND");
        }
        List<ExecutionStepAttempt> attempts = attemptMapper.selectList(
                new LambdaQueryWrapper<ExecutionStepAttempt>()
                        .eq(ExecutionStepAttempt::getExecutionId, executionId)
                        .orderByAsc(ExecutionStepAttempt::getStartedAt,
                                ExecutionStepAttempt::getAttemptNumber));
        return new ExecutionDetailResponse(summary(execution), attempts);
    }

    private ExecutionSummaryResponse summary(IntegrationExecution execution) {
        return new ExecutionSummaryResponse(execution.getId(), execution.getTenantId(),
                execution.getEnvironment(), execution.getApplicationClientId(),
                execution.getRouteDefinitionId(), execution.getReleaseSnapshotId(),
                execution.getExecutionMode(), execution.getExecutionState(), execution.getWorkflowId(),
                execution.getTraceId(), execution.getCallerId(), execution.getStartedAt(),
                execution.getCompletedAt(), execution.getDurationMillis(), execution.getErrorCode(),
                execution.getSanitizedError(), execution.getRetentionUntil());
    }
}
