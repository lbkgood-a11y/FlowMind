package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.service.lowcode.dto.RuntimeWorkflowResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkflowLaunchClient {

    private static final String SERVICE_NAME = "service-lowcode";

    private final InternalServiceSecurityProperties securityProperties;
    private final RestClient restClient;

    public WorkflowLaunchClient(InternalServiceSecurityProperties securityProperties,
                                @Value("${triobase.integrations.workflow.base-url:http://localhost:8086}") String workflowBaseUrl) {
        this.securityProperties = securityProperties;
        this.restClient = RestClient.builder().baseUrl(workflowBaseUrl).build();
    }

    public RuntimeWorkflowResponse start(StartCommand command) {
        JsonNode envelope;
        try {
            envelope = restClient.post()
                    .uri("/internal/v1/process-instances/start")
                    .headers(headers -> {
                        headers.add(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME);
                        headers.add(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken());
                        addSecurityHeaders(headers);
                    })
                    .body(toPayload(command))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new WorkflowLaunchException("WORKFLOW_START_UNAVAILABLE",
                    "WORKFLOW_START_UNAVAILABLE", false);
        }
        if (envelope == null || envelope.path("code").asInt(-1) != 0) {
            String code = envelope != null ? String.valueOf(envelope.path("code").asInt(-1)) : "UNKNOWN";
            String message = envelope != null && StringUtils.hasText(envelope.path("message").asText())
                    ? envelope.path("message").asText() : "WORKFLOW_START_FAILED";
            throw new WorkflowLaunchException(code, message, "40900".equals(code));
        }
        JsonNode data = envelope.path("data");
        if (!data.isObject() || !StringUtils.hasText(data.path("id").asText())) {
            throw new WorkflowLaunchException("WORKFLOW_START_INVALID_RESPONSE",
                    "WORKFLOW_START_INVALID_RESPONSE", false);
        }
        RuntimeWorkflowResponse response = new RuntimeWorkflowResponse();
        response.setProcessInstanceId(data.path("id").asText());
        response.setProcessPackageId(data.path("processPackageId").asText(null));
        response.setProcessKey(data.path("processKey").asText(command.processKey()));
        response.setVersion(data.path("version").isInt() ? data.path("version").asInt() : command.version());
        response.setStatus(data.path("status").asText("RUNNING"));
        return response;
    }

    private Map<String, Object> toPayload(StartCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "processPackageId", command.processPackageId());
        putIfPresent(payload, "version", command.version());
        putIfPresent(payload, "processKey", command.processKey());
        putIfPresent(payload, "title", command.title());
        putIfPresent(payload, "formData", command.formData());
        putIfPresent(payload, "launchMode", command.launchMode());
        putIfPresent(payload, "businessType", command.businessType());
        putIfPresent(payload, "businessId", command.businessId());
        putIfPresent(payload, "idempotencyKey", command.idempotencyKey());
        return payload;
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private void addSecurityHeaders(org.springframework.http.HttpHeaders headers) {
        header(headers, "X-User-Id", SecurityContextHolder.getUserId());
        header(headers, "X-Username", SecurityContextHolder.getUsername());
        header(headers, "X-Tenant-Id", SecurityContextHolder.getTenantId());
        header(headers, "X-User-Roles", join(SecurityContextHolder.getRoles()));
        header(headers, "X-User-Permissions", join(SecurityContextHolder.getPermissions()));
    }

    private void header(org.springframework.http.HttpHeaders headers, String name, String value) {
        if (StringUtils.hasText(value)) {
            headers.add(name, value);
        }
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values);
    }

    public record StartCommand(
            String processPackageId,
            Integer version,
            String processKey,
            String title,
            Map<String, Object> formData,
            String launchMode,
            String businessType,
            String businessId,
            String idempotencyKey
    ) {
    }
}
