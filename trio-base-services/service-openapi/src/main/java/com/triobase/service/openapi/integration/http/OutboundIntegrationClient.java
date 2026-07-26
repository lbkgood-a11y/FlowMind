package com.triobase.service.openapi.integration.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.openapi.entity.ConnectorVersion;
import com.triobase.common.openapi.credential.CredentialMaterial;

import java.util.List;
import java.util.Map;

public interface OutboundIntegrationClient {

    OutboundResponse execute(OutboundRequest request);

    record OutboundRequest(
            ConnectorVersion connector,
            JsonNode body,
            Map<String, List<String>> headers,
            CredentialMaterial credential) {
    }

    record OutboundResponse(
            int status,
            Map<String, List<String>> headers,
            byte[] body,
            long durationMillis) {
    }
}
