package com.triobase.service.openapi.dto;

import com.triobase.common.openapi.enums.Environment;
import com.triobase.common.openapi.enums.ExecutionMode;
import com.triobase.common.openapi.enums.ExecutionState;

import java.time.LocalDateTime;

public record ExecutionSummaryResponse(
        String id,
        String tenantId,
        Environment environment,
        String applicationClientId,
        String routeDefinitionId,
        String releaseSnapshotId,
        ExecutionMode executionMode,
        ExecutionState executionState,
        String workflowId,
        String traceId,
        String callerId,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long durationMillis,
        String errorCode,
        String sanitizedError,
        LocalDateTime retentionUntil) {
}
