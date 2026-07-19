package com.triobase.service.action.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.service.action.exception.ActionRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestActionAuthorizationClient implements ActionAuthorizationClient {

    private static final String SERVICE_NAME = "service-action";

    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RestActionAuthorizationClient(InternalServiceSecurityProperties securityProperties,
                                         ObjectMapper objectMapper,
                                         @Value("${triobase.integrations.auth.base-url:http://localhost:8081}")
                                         String authBaseUrl) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(authBaseUrl).build();
    }

    @Override
    public AuthorizationDecisionResponse decide(AuthorizationDecisionRequest request) {
        try {
            JsonNode envelope = restClient.post()
                    .uri("/internal/v1/authz/decide")
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (envelope == null || envelope.path("code").asInt(-1) != 0 || envelope.path("data").isMissingNode()) {
                throw authorizationFailure(null);
            }
            return objectMapper.treeToValue(envelope.path("data"), AuthorizationDecisionResponse.class);
        } catch (ActionRuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw authorizationFailure(exception);
        }
    }

    private ActionRuntimeException authorizationFailure(Throwable cause) {
        return new ActionRuntimeException(
                50241,
                ActionErrorCategory.AUTHORIZATION,
                "ACTION_AUTHZ_DECISION_FAILED",
                null,
                cause);
    }
}
