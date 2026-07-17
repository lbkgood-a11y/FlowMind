package com.triobase.service.openapi.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveCallbackQuarantineRequest(
        @NotBlank String action,
        String executionId,
        @NotBlank String note) {
}
