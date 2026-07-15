package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.auth.DataScopeProvider;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RemoteDataScopeProvider implements DataScopeProvider {

    private static final String SERVICE_NAME = "service-lowcode";

    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RemoteDataScopeProvider(InternalServiceSecurityProperties securityProperties,
                                   ObjectMapper objectMapper,
                                   @Value("${triobase.integrations.auth.base-url:http://localhost:8081}") String authBaseUrl) {
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
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
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
