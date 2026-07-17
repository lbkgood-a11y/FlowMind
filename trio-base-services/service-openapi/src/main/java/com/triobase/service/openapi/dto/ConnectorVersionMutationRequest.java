package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.ConnectorOperationClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConnectorVersionMutationRequest(
        @NotBlank String baseUrl,
        @NotBlank String operationPath,
        @NotBlank String httpMethod,
        int timeoutMillis,
        @NotNull ConnectorOperationClass operationClass,
        @NotNull AuthenticationType authenticationType,
        String secretReference,
        JsonNode networkPolicy,
        long responseSizeLimit) {
}
