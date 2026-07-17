package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;

import java.time.LocalDateTime;

public record OrchestrationVersionResponse(
        String orchestrationId,
        String versionId,
        String tenantId,
        String orchestrationKey,
        String displayName,
        String ownerId,
        int versionNumber,
        VersionLifecycleState lifecycleState,
        String schemaVersion,
        JsonNode definitionContent,
        String definitionHash,
        JsonNode validationResult,
        String publishedBy,
        LocalDateTime publishedAt) {
}
