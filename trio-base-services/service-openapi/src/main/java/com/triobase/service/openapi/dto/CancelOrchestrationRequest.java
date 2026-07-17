package com.triobase.service.openapi.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelOrchestrationRequest(@NotBlank String reason) {
}
