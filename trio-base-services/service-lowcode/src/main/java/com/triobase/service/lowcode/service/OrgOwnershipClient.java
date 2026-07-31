package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.dto.internal.OrgOwnershipResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class OrgOwnershipClient {

    private static final String SERVICE_NAME = "service-lowcode";
    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OrgOwnershipClient(InternalServiceSecurityProperties securityProperties,
                              ObjectMapper objectMapper,
                              @Value("${triobase.integrations.org.base-url:http://localhost:8082}") String baseUrl) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public OrgOwnershipResponse primaryOwnership(String tenantId, String userId) {
        try {
            JsonNode envelope = restClient.get()
                    .uri(builder -> builder.path("/internal/v1/org-ownership/users/{userId}/primary")
                            .queryParam("tenantId", tenantId).build(userId))
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                    .retrieve().body(JsonNode.class);
            if (envelope == null || envelope.path("code").asInt(-1) != 0
                    || envelope.path("data").isMissingNode()) {
                throw new BizException(50293, "LOWCODE_ORG_OWNERSHIP_RESOLUTION_FAILED");
            }
            return objectMapper.treeToValue(envelope.path("data"), OrgOwnershipResponse.class);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(50293, "LOWCODE_ORG_OWNERSHIP_RESOLUTION_FAILED");
        }
    }
}
