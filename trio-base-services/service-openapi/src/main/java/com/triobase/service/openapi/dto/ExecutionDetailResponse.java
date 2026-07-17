package com.triobase.service.openapi.dto;

import com.triobase.service.openapi.domain.entity.ExecutionStepAttempt;

import java.util.List;

public record ExecutionDetailResponse(
        ExecutionSummaryResponse execution,
        List<ExecutionStepAttempt> attempts) {
}
