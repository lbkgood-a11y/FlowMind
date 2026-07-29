package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionResponse;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class AuthorizationDecisionClient {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationDecisionClient.class);
    private static final String SERVICE_NAME = "service-lowcode";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BASE_DELAY_MS = 200;

    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AuthorizationDecisionClient(InternalServiceSecurityProperties securityProperties,
                                       ObjectMapper objectMapper,
                                       @Value("${triobase.integrations.auth.base-url:http://localhost:8081}") String authBaseUrl) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(authBaseUrl)
                .requestFactory(factory)
                .build();
    }

    public AuthorizationDecisionResponse decide(AuthorizationDecisionRequest request) {
        JsonNode envelope = post("/internal/v1/authz/decide", request);
        return convert(envelope, AuthorizationDecisionResponse.class);
    }

    public AuthorizationBatchDecisionResponse batchDecide(AuthorizationBatchDecisionRequest request) {
        JsonNode envelope = post("/internal/v1/authz/batch-decide", request);
        return convert(envelope, AuthorizationBatchDecisionResponse.class);
    }

    private JsonNode post(String path, Object request) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                JsonNode envelope = restClient.post()
                        .uri(path)
                        .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                        .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                        .body(request)
                        .retrieve()
                        .body(JsonNode.class);
                if (envelope == null || envelope.path("code").asInt(-1) != 0 || envelope.path("data").isMissingNode()) {
                    throw new BizException(50291, "LOWCODE_AUTHZ_DECISION_FAILED");
                }
                return envelope.path("data");
            } catch (BizException exception) {
                throw exception;
            } catch (Exception exception) {
                lastException = exception;
                if (attempt < MAX_RETRIES) {
                    long jitter = ThreadLocalRandom.current().nextLong(RETRY_BASE_DELAY_MS);
                    long delay = RETRY_BASE_DELAY_MS * (1L << attempt) + jitter;
                    log.warn("Authz decision failed (attempt {}/{}), retrying in {}ms — path={}",
                            attempt + 1, MAX_RETRIES + 1, delay, path, exception);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("Authz decision failed after {} retries — path={}", MAX_RETRIES + 1, path, lastException);
        throw new BizException(50291, "LOWCODE_AUTHZ_DECISION_FAILED");
    }

    private <T> T convert(JsonNode data, Class<T> type) {
        try {
            return objectMapper.treeToValue(data, type);
        } catch (Exception exception) {
            throw new BizException(50291, "LOWCODE_AUTHZ_DECISION_FAILED");
        }
    }
}
