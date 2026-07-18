package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.service.lowcode.dto.ApplicationActionRequest;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ApplicationReferenceValidator {

    private static final String SERVICE_NAME = "service-lowcode";
    private static final Set<String> WORKFLOW_ACTION_TYPES = Set.of(
            "SUBMIT_AND_LAUNCH_WORKFLOW", "RETRY_WORKFLOW");

    private final InternalServiceSecurityProperties securityProperties;
    private final RestClient authClient;
    private final RestClient workflowClient;

    public ApplicationReferenceValidator(InternalServiceSecurityProperties securityProperties,
                                         @Value("${triobase.integrations.auth.base-url:http://localhost:8081}") String authBaseUrl,
                                         @Value("${triobase.integrations.workflow.base-url:http://localhost:8086}") String workflowBaseUrl) {
        this.securityProperties = securityProperties;
        this.authClient = RestClient.builder().baseUrl(authBaseUrl).build();
        this.workflowClient = RestClient.builder().baseUrl(workflowBaseUrl).build();
    }

    public void validatePublication(LcApplicationVersion version, List<ApplicationActionRequest> actions) {
        if (!StringUtils.hasText(version.getViewPermissionCode())) {
            throw new BizException(40050, "APPLICATION_VIEW_PERMISSION_REQUIRED");
        }
        validatePermissions(version, actions);
        validateWorkflowBindings(actions);
    }

    private void validatePermissions(LcApplicationVersion version, List<ApplicationActionRequest> actions) {
        List<String> permissionCodes = collectPermissionCodes(version, actions);
        JsonNode envelope;
        try {
            envelope = authClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/internal/v1/permissions/missing");
                        permissionCodes.forEach(code -> builder.queryParam("codes", code));
                        return builder.build();
                    })
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new BizException(50250, "APPLICATION_PERMISSION_REGISTRY_UNAVAILABLE");
        }
        if (envelope == null || envelope.path("code").asInt(-1) != 0 || !envelope.path("data").isArray()) {
            throw new BizException(50250, "APPLICATION_PERMISSION_REGISTRY_UNAVAILABLE");
        }
        List<String> missing = new ArrayList<>();
        for (JsonNode node : envelope.path("data")) {
            if (node.isTextual()) {
                missing.add(node.asText());
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(40050, "APPLICATION_PERMISSION_NOT_REGISTERED");
        }
    }

    private List<String> collectPermissionCodes(LcApplicationVersion version, List<ApplicationActionRequest> actions) {
        Set<String> codes = new LinkedHashSet<>();
        codes.add(version.getViewPermissionCode().trim());
        if (actions != null) {
            for (ApplicationActionRequest action : actions) {
                if (action == null) {
                    continue;
                }
                if (!StringUtils.hasText(action.getPermissionCode())) {
                    throw new BizException(40050, "APPLICATION_ACTION_PERMISSION_REQUIRED");
                }
                codes.add(action.getPermissionCode().trim());
            }
        }
        return List.copyOf(codes);
    }

    private void validateWorkflowBindings(List<ApplicationActionRequest> actions) {
        if (actions == null) {
            return;
        }
        Set<String> processKeys = new LinkedHashSet<>();
        for (ApplicationActionRequest action : actions) {
            if (action == null || !WORKFLOW_ACTION_TYPES.contains(normalize(action.getActionType()))) {
                continue;
            }
            processKeys.add(action.getProcessKey().trim());
        }
        for (String processKey : processKeys) {
            if (!publishedProcessExists(processKey)) {
                throw new BizException(40050, "APPLICATION_PROCESS_BINDING_NOT_FOUND");
            }
        }
    }

    private boolean publishedProcessExists(String processKey) {
        JsonNode envelope;
        try {
            envelope = workflowClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/process-packages/published/exists")
                            .queryParam("processKey", processKey)
                            .build())
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new BizException(50250, "APPLICATION_WORKFLOW_REGISTRY_UNAVAILABLE");
        }
        if (envelope == null || envelope.path("code").asInt(-1) != 0 || !envelope.path("data").isBoolean()) {
            throw new BizException(50250, "APPLICATION_WORKFLOW_REGISTRY_UNAVAILABLE");
        }
        return envelope.path("data").asBoolean(false);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
