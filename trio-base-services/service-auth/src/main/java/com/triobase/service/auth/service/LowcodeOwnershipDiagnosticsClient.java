package com.triobase.service.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class LowcodeOwnershipDiagnosticsClient {
    private static final String SERVICE_NAME = "service-auth";
    private final InternalServiceSecurityProperties securityProperties;
    private final RestClient restClient;

    public LowcodeOwnershipDiagnosticsClient(InternalServiceSecurityProperties securityProperties,
            @Value("${triobase.integrations.lowcode.base-url:http://localhost:8085}") String baseUrl) {
        this.securityProperties = securityProperties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public long unresolvedCount(String tenantId) {
        try {
            JsonNode envelope = restClient.get()
                    .uri(builder -> builder.path("/internal/v1/form-ownership/unresolved-count")
                            .queryParam("tenantId", tenantId).build())
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                    .retrieve().body(JsonNode.class);
            if (envelope == null || envelope.path("code").asInt(-1) != 0 || !envelope.has("data")) {
                throw new BizException(50294, "LOWCODE_OWNERSHIP_DIAGNOSTICS_FAILED");
            }
            return envelope.path("data").asLong();
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(50294, "LOWCODE_OWNERSHIP_DIAGNOSTICS_FAILED");
        }
    }
}
