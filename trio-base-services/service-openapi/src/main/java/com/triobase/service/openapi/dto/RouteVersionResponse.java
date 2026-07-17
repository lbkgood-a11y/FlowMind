package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;

import java.time.LocalDateTime;

public record RouteVersionResponse(
        String routeId,
        String routeVersionId,
        String tenantId,
        String routeKey,
        String displayName,
        Integer versionNumber,
        Environment environment,
        VersionLifecycleState lifecycleState,
        Integer priority,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveUntil,
        Boolean enabled,
        JsonNode routePredicate,
        ExecutionMode executionMode,
        String connectorVersionId,
        String requestMappingVersionId,
        String responseMappingVersionId,
        String orchestrationVersionId) {
}
