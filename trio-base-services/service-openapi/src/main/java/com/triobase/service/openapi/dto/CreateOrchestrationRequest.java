package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrchestrationRequest(
        String tenantId,
        @NotBlank String orchestrationKey,
        @NotBlank String displayName,
        @NotBlank String ownerId,
        @NotBlank String schemaVersion,
        @NotNull JsonNode definitionContent) {
}
