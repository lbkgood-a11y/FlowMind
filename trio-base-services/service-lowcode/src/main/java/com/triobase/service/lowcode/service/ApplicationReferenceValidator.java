package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.service.lowcode.dto.ApplicationActionRequest;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
/**
 * 在低代码应用发布前验证跨服务引用是否真实可用。
 *
 * <p>校验采用失败关闭：权限注册中心或工作流注册中心不可达、返回契约异常时，
 * 发布必须失败，避免产生“应用已发布但权限或流程不可执行”的不一致版本。</p>
 */
public class ApplicationReferenceValidator {

    private static final String SERVICE_NAME = "service-lowcode";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> WORKFLOW_ACTION_TYPES = Set.of(
            "SUBMIT_AND_LAUNCH_WORKFLOW", "RETRY_WORKFLOW");

    private final InternalServiceSecurityProperties securityProperties;
    private final RestClient authClient;
    private final RestClient workflowClient;

    public ApplicationReferenceValidator(InternalServiceSecurityProperties securityProperties,
                                         @Value("${triobase.integrations.auth.base-url:http://localhost:8081}") String authBaseUrl,
                                         @Value("${triobase.integrations.workflow.base-url:http://localhost:8086}") String workflowBaseUrl) {
        this.securityProperties = securityProperties;
        this.authClient = buildRestClient(authBaseUrl);
        this.workflowClient = buildRestClient(workflowBaseUrl);
    }

    private static RestClient buildRestClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
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
            /*
             * tenantId 必须来自待发布版本，而不能依赖调用线程的默认租户。
             * 发布校验可能由后台流程触发，遗漏该参数会错误查询 default 租户并误报未注册。
             */
            envelope = authClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/internal/v1/authz/codes/missing");
                        permissionCodes.forEach(code -> builder.queryParam("codes", code));
                        builder.queryParam("tenantId", version.getTenantId());
                        return builder.build();
                    })
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            // 注册中心异常不能被解释为“没有缺失编码”；该状态必须保留为可重试的基础设施错误。
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
        // LinkedHashSet 同时去重并保持声明顺序，使错误诊断和发布审计具有稳定输出。
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
