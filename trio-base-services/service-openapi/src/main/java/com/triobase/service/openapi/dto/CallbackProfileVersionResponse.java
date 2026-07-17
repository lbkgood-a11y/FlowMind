package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.CallbackCorrelationType;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;

import java.time.LocalDateTime;

public record CallbackProfileVersionResponse(
        String profileId,
        String versionId,
        String tenantId,
        String callbackKey,
        String displayName,
        String ownerId,
        int versionNumber,
        VersionLifecycleState lifecycleState,
        Environment environment,
        String applicationClientId,
        AuthenticationType authenticationType,
        String requestStructureVersionId,
        String inboundMappingVersionId,
        String partnerEventIdPointer,
        String correlationPointer,
        CallbackCorrelationType correlationType,
        String signalName,
        long replayWindowSeconds,
        long maxBodyBytes,
        long callbackPerMinute,
        int acknowledgementStatus,
        String acknowledgementContentType,
        String acknowledgementBody,
        JsonNode securityPolicy,
        JsonNode validationResult,
        String publishedBy,
        LocalDateTime publishedAt) {
}
