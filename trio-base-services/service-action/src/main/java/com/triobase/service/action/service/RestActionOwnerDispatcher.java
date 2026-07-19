package com.triobase.service.action.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerAdapterSupport;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.action.owner.ActionOwnerDispatchResponse;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.action.config.ActionOwnerServiceProperties;
import com.triobase.service.action.entity.ActionExecution;
import com.triobase.service.action.exception.ActionRuntimeException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestActionOwnerDispatcher implements ActionOwnerDispatcher {

    private static final String SERVICE_NAME = "service-action";
    private static final String OWNER_EXECUTE_PATH = "/internal/v1/actions/execute";

    private final ActionOwnerServiceProperties ownerProperties;
    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final ActionOwnerAdapterSupport adapterSupport = new ActionOwnerAdapterSupport();

    public RestActionOwnerDispatcher(ActionOwnerServiceProperties ownerProperties,
                                     InternalServiceSecurityProperties securityProperties,
                                     ObjectMapper objectMapper,
                                     RestClient.Builder restClientBuilder) {
        this.ownerProperties = ownerProperties;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public GlobalActionResult dispatch(ActionDefinition definition,
                                       GlobalActionRequest request,
                                       ActionExecution execution) {
        String baseUrl = ownerProperties.requireBaseUrl(definition.getOwnerService());
        if (baseUrl == null) {
            throw new ActionRuntimeException(
                    50243,
                    ActionErrorCategory.DISPATCH,
                    "ACTION_OWNER_ROUTE_NOT_CONFIGURED",
                    "ownerService",
                    null);
        }
        try {
            JsonNode envelope = restClient.post()
                    .uri(baseUrl + OWNER_EXECUTE_PATH)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                    .header(TraceUtil.TRACE_ID_KEY, firstNonBlank(
                            request.getContext() != null ? request.getContext().getTraceId() : null,
                            execution.getTraceId()))
                    .header("X-Action-Id", execution.getId())
                    .header("X-Action-Type", definition.getActionType())
                    .header("X-Action-Source", request.getSource() != null ? request.getSource().name() : "")
                    .header("X-Action-Target-Type", headerValue(request.getTarget() != null ? request.getTarget().getType() : null))
                    .header("X-Action-Target-Id", headerValue(request.getTarget() != null ? request.getTarget().getId() : null))
                    .header("X-Action-Correlation-Id", headerValue(request.getContext() != null ? request.getContext().getCorrelationId() : null))
                    .header("X-Action-Idempotency-Key", headerValue(request.getIdempotencyKey()))
                    .body(toOwnerRequest(definition, request, execution))
                    .retrieve()
                    .body(JsonNode.class);
            if (envelope == null || envelope.path("code").asInt(-1) != 0 || envelope.path("data").isMissingNode()) {
                throw ownerFailure(null);
            }
            ActionOwnerDispatchResponse response = objectMapper.treeToValue(
                    envelope.path("data"),
                    ActionOwnerDispatchResponse.class);
            GlobalActionResult result = adapterSupport.toGlobalResult(response);
            result.setActionType(definition.getActionType());
            result.setTarget(request.getTarget());
            return result;
        } catch (ActionRuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw ownerFailure(exception);
        }
    }

    private ActionOwnerDispatchRequest toOwnerRequest(ActionDefinition definition,
                                                      GlobalActionRequest request,
                                                      ActionExecution execution) {
        ActionOwnerDispatchRequest ownerRequest = new ActionOwnerDispatchRequest();
        ownerRequest.setActionId(execution.getId());
        ownerRequest.setActionType(definition.getActionType());
        ownerRequest.setSource(request.getSource());
        ownerRequest.setActor(request.getActor());
        ownerRequest.setTarget(request.getTarget());
        ownerRequest.setPayload(request.getPayload());
        ownerRequest.setContext(request.getContext());
        ownerRequest.setIdempotencyKey(request.getIdempotencyKey());
        ownerRequest.setExecutionMode(definition.getExecutionMode());
        ownerRequest.setOwnerService(definition.getOwnerService());
        return ownerRequest;
    }

    private ActionRuntimeException ownerFailure(Throwable cause) {
        return new ActionRuntimeException(
                50244,
                ActionErrorCategory.DISPATCH,
                "ACTION_OWNER_DISPATCH_FAILED",
                null,
                cause);
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private String headerValue(String value) {
        return value != null ? value : "";
    }
}
