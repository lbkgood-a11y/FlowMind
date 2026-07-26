package com.triobase.service.openapi.dto;

import com.triobase.common.openapi.entity.ExecutionStepAttempt;

import java.util.List;

public record ExecutionDetailResponse(
        ExecutionSummaryResponse execution,
        List<ExecutionStepAttempt> attempts) {
}
