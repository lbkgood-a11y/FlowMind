package com.triobase.service.org.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthzFieldRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * 从 service-auth 获取组织字段规则，由 service-org 在 Owner 边界内执行。
 *
 * <p>授权服务不能直接读取组织表；本客户端只传递字段决策。请求失败时禁止返回“无限制”规则，
 * 否则授权基础设施故障会扩大敏感组织字段的可见范围。</p>
 */
@Component
public class OrgFieldDecisionClient {

    private static final String SERVICE_NAME = "service-org";

    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OrgFieldDecisionClient(InternalServiceSecurityProperties securityProperties,
                                  ObjectMapper objectMapper,
                                  @Value("${triobase.integrations.auth.base-url:http://localhost:8081}") String authBaseUrl) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(authBaseUrl).requestFactory(factory).build();
    }

    public List<AuthzFieldRule> effectiveRules(String tenantId, String userId, String resourceCode,
                                                List<String> fieldKeys) {
        AuthorizationDecisionRequest request = new AuthorizationDecisionRequest();
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setOwnerService(SERVICE_NAME);
        request.setResourceCode(resourceCode);
        request.setFieldKeys(fieldKeys);
        request.setEnforcementMode(true);
        try {
            JsonNode envelope = restClient.post()
                    .uri("/internal/v1/authz/field-rules/effective")
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (envelope == null || envelope.path("code").asInt(-1) != 0 || !envelope.has("data")) {
                throw new BizException(50292, "ORG_FIELD_AUTHZ_DECISION_FAILED");
            }
            return objectMapper.convertValue(envelope.path("data"), objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, AuthzFieldRule.class));
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(50292, "ORG_FIELD_AUTHZ_DECISION_FAILED");
        }
    }
}
