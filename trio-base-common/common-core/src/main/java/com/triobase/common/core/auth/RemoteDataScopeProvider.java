package com.triobase.common.core.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import org.springframework.web.client.RestClient;

public class RemoteDataScopeProvider implements DataScopeProvider {

    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RemoteDataScopeProvider(InternalServiceSecurityProperties securityProperties,
                                   ObjectMapper objectMapper) {
        this(securityProperties, objectMapper, "http://localhost:8081");
    }

    public RemoteDataScopeProvider(InternalServiceSecurityProperties securityProperties,
                                   ObjectMapper objectMapper,
                                   String authBaseUrl) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(authBaseUrl).build();
    }

    @Override
    public DataScope resolve(String userId, String resourceCode, String actionCode) {
        try {
            JsonNode envelope = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/data-scopes/effective")
                            .queryParam("userId", userId)
                            .queryParam("resourceCode", resourceCode)
                            .queryParam("actionCode", actionCode)
                            .build())
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, "common-core")
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                    .retrieve()
                    .body(JsonNode.class);
            if (envelope == null || envelope.path("code").asInt(-1) != 0 || envelope.path("data").isMissingNode()) {
                return DataScope.restrictive(userId, resourceCode, actionCode);
            }
            return objectMapper.treeToValue(envelope.path("data"), DataScope.class);
        } catch (Exception ignored) {
            return DataScope.restrictive(userId, resourceCode, actionCode);
        }
    }
}
