package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrchestrationVersionMutationRequest(
        @NotBlank String schemaVersion,
        @NotNull JsonNode definitionContent) {
}
