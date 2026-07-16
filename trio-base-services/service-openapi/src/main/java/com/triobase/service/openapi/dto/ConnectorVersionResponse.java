package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.ConnectorOperationClass;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;

public record ConnectorVersionResponse(
        String connectorId,
        String connectorVersionId,
        String tenantId,
        String connectorKey,
        String displayName,
        Integer versionNumber,
        VersionLifecycleState lifecycleState,
        String baseUrl,
        String operationPath,
        String httpMethod,
        Integer timeoutMillis,
        ConnectorOperationClass operationClass,
        AuthenticationType authenticationType,
        String secretReference,
        JsonNode networkPolicy,
        Long responseSizeLimit) {
}
