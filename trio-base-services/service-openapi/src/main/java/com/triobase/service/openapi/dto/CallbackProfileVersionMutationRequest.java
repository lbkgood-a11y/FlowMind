package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.CallbackCorrelationType;
import com.triobase.service.openapi.domain.enums.Environment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CallbackProfileVersionMutationRequest(
        @NotNull Environment environment,
        @NotBlank String applicationClientId,
        @NotNull AuthenticationType authenticationType,
        @NotBlank String secretReference,
        @NotBlank String requestStructureVersionId,
        String inboundMappingVersionId,
        @NotBlank String partnerEventIdPointer,
        @NotBlank String correlationPointer,
        @NotNull CallbackCorrelationType correlationType,
        @NotBlank String signalName,
        @Min(30) @Max(86400) long replayWindowSeconds,
        @Min(1) @Max(10485760) long maxBodyBytes,
        @Min(1) long callbackPerMinute,
        @Min(200) @Max(299) int acknowledgementStatus,
        @NotBlank String acknowledgementContentType,
        @NotBlank String acknowledgementBody,
        JsonNode securityPolicy) {
}
