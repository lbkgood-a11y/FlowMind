package com.triobase.common.core.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

public class RemoteDataScopeProvider implements DataScopeProvider {

    private static final Logger log = LoggerFactory.getLogger(RemoteDataScopeProvider.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BASE_DELAY_MS = 200;

    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String serviceName;

    public RemoteDataScopeProvider(InternalServiceSecurityProperties securityProperties,
                                   ObjectMapper objectMapper) {
        this(securityProperties, objectMapper, "http://localhost:8081", "common-core");
    }

    public RemoteDataScopeProvider(InternalServiceSecurityProperties securityProperties,
                                   ObjectMapper objectMapper,
                                   String authBaseUrl) {
        this(securityProperties, objectMapper, authBaseUrl, "common-core");
    }

    public RemoteDataScopeProvider(InternalServiceSecurityProperties securityProperties,
                                   ObjectMapper objectMapper,
                                   String authBaseUrl,
                                   String serviceName) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(authBaseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public DataScope resolve(String userId, String resourceCode, String actionCode, String tenantId) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                JsonNode envelope = restClient.get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder
                                    .path("/internal/v1/data-scopes/effective")
                                    .queryParam("userId", userId)
                                    .queryParam("resourceCode", resourceCode)
                                    .queryParam("actionCode", actionCode);
                            if (tenantId != null && !tenantId.isBlank()) {
                                builder.queryParam("tenantId", tenantId);
                            }
                            return builder.build();
                        })
                        .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, serviceName)
                        .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                        .retrieve()
                        .body(JsonNode.class);
                if (envelope == null || envelope.path("code").asInt(-1) != 0 || envelope.path("data").isMissingNode()) {
                    return DataScope.restrictive(userId, resourceCode, actionCode);
                }
                return objectMapper.treeToValue(envelope.path("data"), DataScope.class);
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << attempt);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.warn("Failed to resolve data scope from auth service for user={} resource={} action={} after {} attempts — falling back to restrictive",
                userId, resourceCode, actionCode, MAX_RETRIES + 1, lastException);
        return DataScope.restrictive(userId, resourceCode, actionCode);
    }
}
