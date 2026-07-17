package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record StartOrchestrationRequest(@NotNull JsonNode payload) {
}
