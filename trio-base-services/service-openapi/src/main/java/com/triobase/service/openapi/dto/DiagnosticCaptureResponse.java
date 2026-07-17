package com.triobase.service.openapi.dto;

import java.time.LocalDateTime;

public record DiagnosticCaptureResponse(
        String diagnosticId,
        String executionId,
        LocalDateTime expiresAt) {
}
