package com.triobase.common.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.openapi.enums.ExecutionState;

public record OrchestrationExecutionResponse(
        String executionId,
        String workflowId,
        String workflowRunId,
        ExecutionState state,
        boolean attachedToExisting,
        String traceId,
        JsonNode details) {
}
