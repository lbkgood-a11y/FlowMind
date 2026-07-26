package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.openapi.enums.AuthenticationType;
import com.triobase.common.openapi.enums.ConnectorOperationClass;
import com.triobase.common.openapi.enums.VersionLifecycleState;

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
