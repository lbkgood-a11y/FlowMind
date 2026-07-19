package com.triobase.service.openapi.service.authorization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestCustomDocumentDecisionClient implements CustomDocumentDecisionClient {

    private static final String SERVICE_NAME = "service-openapi";

    private final InternalServiceSecurityProperties securityProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public RestCustomDocumentDecisionClient(
            InternalServiceSecurityProperties securityProperties,
            @Value("${triobase.integrations.auth.base-url:http://localhost:8081}") String authBaseUrl,
            ObjectMapper objectMapper) {
        this(securityProperties, RestClient.builder().baseUrl(authBaseUrl).build(), objectMapper);
    }

    RestCustomDocumentDecisionClient(
            InternalServiceSecurityProperties securityProperties,
            RestClient restClient,
            ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuthorizationDecisionResponse decide(AuthorizationDecisionRequest request) {
        JsonNode envelope = restClient.post()
                .uri("/internal/v1/authz/decide")
                .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        if (envelope == null || envelope.path("code").asInt(-1) != 0 || envelope.get("data") == null) {
            throw new BizException(50291, "CUSTOM_DOC_AUTHZ_DECISION_FAILED");
        }
        return objectMapper.convertValue(envelope.get("data"), AuthorizationDecisionResponse.class);
    }
}
