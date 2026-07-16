package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;

import java.time.LocalDateTime;

public record StructureVersionResponse(
        String id,
        String structureId,
        Integer versionNumber,
        Integer compatibilityLine,
        VersionLifecycleState lifecycleState,
        JsonNode schemaContent,
        String schemaHash,
        String parentStructureVersionId,
        String changeSummary,
        JsonNode semanticChange,
        JsonNode compatibilityResult,
        String publishedBy,
        LocalDateTime publishedAt,
        LocalDateTime deprecatedAt,
        LocalDateTime archivedAt) {
}
