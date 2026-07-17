package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateRouteRequest(
        String tenantId,
        @NotBlank String routeKey,
        @NotBlank String displayName,
        @NotBlank String ownerId,
        @NotNull Environment environment,
        int priority,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveUntil,
        boolean enabled,
        JsonNode routePredicate,
        @NotNull ExecutionMode executionMode,
        String connectorVersionId,
        String requestMappingVersionId,
        String responseMappingVersionId,
        String orchestrationVersionId) {
}
