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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuthorizationDecisionClient {

    private static final String SERVICE_NAME = "service-lowcode";

    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AuthorizationDecisionClient(InternalServiceSecurityProperties securityProperties,
                                       ObjectMapper objectMapper,
                                       @Value("${triobase.integrations.auth.base-url:http://localhost:8081}") String authBaseUrl) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(authBaseUrl).build();
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
            throw new BizException(50291, "LOWCODE_AUTHZ_DECISION_FAILED");
        }
    }

    private <T> T convert(JsonNode data, Class<T> type) {
        try {
            return objectMapper.treeToValue(data, type);
        } catch (Exception exception) {
            throw new BizException(50291, "LOWCODE_AUTHZ_DECISION_FAILED");
        }
    }
}
