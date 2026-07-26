package com.triobase.service.openapi.dto;

import com.triobase.common.openapi.enums.Environment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RollbackReleaseRequest(
        @NotNull Environment environment,
        @NotBlank String targetReleaseId) {
}
