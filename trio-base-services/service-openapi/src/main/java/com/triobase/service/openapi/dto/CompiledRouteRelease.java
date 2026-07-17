package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;

public record CompiledRouteRelease(
        String tenantId,
        Environment environment,
        String routeKey,
        String routeId,
        String routeVersionId,
        String releaseId,
        long policyVersion,
        String snapshotHash,
        JsonNode pinnedDependencies) {
}
