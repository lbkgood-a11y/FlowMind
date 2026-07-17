package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;

import java.time.LocalDateTime;

public record ReleaseSnapshotResponse(
        String releaseId,
        String tenantId,
        Environment environment,
        String routeId,
        String routeVersionId,
        Integer releaseNumber,
        VersionLifecycleState lifecycleState,
        JsonNode pinnedDependencies,
        String snapshotHash,
        JsonNode validationResult,
        String releaseNotes,
        String publishedBy,
        LocalDateTime publishedAt) {
}
