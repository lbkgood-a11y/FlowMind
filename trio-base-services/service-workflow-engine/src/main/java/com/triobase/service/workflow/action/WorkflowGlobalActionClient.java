package com.triobase.service.workflow.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.core.trace.TraceUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WorkflowGlobalActionClient {

    private static final String SERVICE_NAME = "service-workflow-engine";

    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public WorkflowGlobalActionClient(InternalServiceSecurityProperties securityProperties,
                                      ObjectMapper objectMapper,
                                      RestClient.Builder restClientBuilder,
                                      @Value("${triobase.integrations.action.base-url:http://localhost:8089}")
                                      String actionBaseUrl) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(actionBaseUrl).build();
    }

    public GlobalActionResult submit(GlobalActionRequest request) {
        try {
            JsonNode envelope = restClient.post()
                    .uri("/api/v1/actions")
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                    .header(TraceUtil.TRACE_ID_KEY, traceId(request))
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (envelope == null || envelope.path("code").asInt(-1) != 0 || envelope.path("data").isMissingNode()) {
                throw new BizException(50265, "WORKFLOW_GLOBAL_ACTION_FAILED");
            }
            return objectMapper.treeToValue(envelope.path("data"), GlobalActionResult.class);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(50265, "WORKFLOW_GLOBAL_ACTION_FAILED");
        }
    }

    private String traceId(GlobalActionRequest request) {
        ActionContext context = request != null ? request.getContext() : null;
        return context != null ? context.getTraceId() : null;
    }
}
