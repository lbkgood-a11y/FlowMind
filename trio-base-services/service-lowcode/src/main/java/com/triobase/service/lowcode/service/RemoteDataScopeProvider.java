package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.auth.DataScopeProvider;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RemoteDataScopeProvider implements DataScopeProvider {

    private static final Logger log = LoggerFactory.getLogger(RemoteDataScopeProvider.class);
    private static final String SERVICE_NAME = "service-lowcode";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BASE_DELAY_MS = 200;

    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RemoteDataScopeProvider(InternalServiceSecurityProperties securityProperties,
                                   ObjectMapper objectMapper,
                                   @Value("${triobase.integrations.auth.base-url:http://localhost:8081}") String authBaseUrl) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(authBaseUrl).requestFactory(factory).build();
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
                            String effectiveTenant = tenantId != null ? tenantId
                                    : SecurityContextHolder.getTenantId();
                            if (effectiveTenant != null && !effectiveTenant.isBlank()) {
                                builder.queryParam("tenantId", effectiveTenant);
                            }
                            return builder.build();
                        })
                        .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
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
                    long jitter = ThreadLocalRandom.current().nextLong(RETRY_BASE_DELAY_MS);
                    long delay = RETRY_BASE_DELAY_MS * (1L << attempt) + jitter;
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.warn("Data scope resolution failed after {} attempts — returning restrictive scope. "
                + "userId={} resourceCode={} actionCode={}",
                MAX_RETRIES + 1, userId, resourceCode, actionCode, lastException);
        return DataScope.restrictive(userId, resourceCode, actionCode);
    }
}
