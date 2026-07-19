package com.triobase.service.action.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.action.config.ActionOwnerServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class RestActionOwnerGuardChecker implements ActionOwnerGuardChecker {

    private static final String SERVICE_NAME = "service-action";
    private static final String OWNER_GUARD_PATH = "/internal/v1/actions/guard";

    private final ActionOwnerServiceProperties ownerProperties;
    private final InternalServiceSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RestActionOwnerGuardChecker(ActionOwnerServiceProperties ownerProperties,
                                       InternalServiceSecurityProperties securityProperties,
                                       ObjectMapper objectMapper,
                                       RestClient.Builder restClientBuilder) {
        this.ownerProperties = ownerProperties;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public ActionOwnerGuardResponse check(ActionDefinition definition, GlobalActionRequest request) {
        if (definition.getRequiredGuards() == null || definition.getRequiredGuards().isEmpty()) {
            return ActionOwnerGuardResponse.allowed("ACTION_OWNER_GUARD_NOT_REQUIRED");
        }
        String baseUrl = ownerProperties.requireBaseUrl(definition.getOwnerService());
        if (baseUrl == null) {
            return ActionOwnerGuardResponse.denied(
                    "ACTION_OWNER_GUARD_ROUTE_NOT_CONFIGURED",
                    "ACTION_OWNER_GUARD_ROUTE_NOT_CONFIGURED",
                    List.of(ActionError.of(
                            "ACTION_OWNER_GUARD_ROUTE_NOT_CONFIGURED",
                            ActionErrorCategory.DISPATCH,
                            "ACTION_OWNER_GUARD_ROUTE_NOT_CONFIGURED")));
        }
        try {
            JsonNode envelope = restClient.post()
                    .uri(baseUrl + OWNER_GUARD_PATH)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                    .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                    .header(TraceUtil.TRACE_ID_KEY, request.getContext() != null
                            ? headerValue(request.getContext().getTraceId())
                            : "")
                    .header("X-Action-Type", definition.getActionType())
                    .body(toOwnerRequest(definition, request))
                    .retrieve()
                    .body(JsonNode.class);
            if (envelope == null || envelope.path("code").asInt(-1) != 0 || envelope.path("data").isMissingNode()) {
                return denied("ACTION_OWNER_GUARD_FAILED");
            }
            return objectMapper.treeToValue(envelope.path("data"), ActionOwnerGuardResponse.class);
        } catch (HttpClientErrorException.NotFound ignored) {
            return ActionOwnerGuardResponse.allowed("ACTION_OWNER_GUARD_NOT_IMPLEMENTED");
        } catch (Exception exception) {
            return denied("ACTION_OWNER_GUARD_FAILED");
        }
    }

    private ActionOwnerDispatchRequest toOwnerRequest(ActionDefinition definition, GlobalActionRequest request) {
        ActionOwnerDispatchRequest ownerRequest = new ActionOwnerDispatchRequest();
        ownerRequest.setActionId(request.getActionId());
        ownerRequest.setActionType(definition.getActionType());
        ownerRequest.setSource(request.getSource());
        ownerRequest.setActor(request.getActor());
        ownerRequest.setTarget(request.getTarget());
        ownerRequest.setPayload(request.getPayload());
        ownerRequest.setContext(request.getContext());
        ownerRequest.setIdempotencyKey(request.getIdempotencyKey());
        ownerRequest.setExecutionMode(request.getExecutionMode());
        ownerRequest.setOwnerService(definition.getOwnerService());
        return ownerRequest;
    }

    private ActionOwnerGuardResponse denied(String code) {
        return ActionOwnerGuardResponse.denied(
                code,
                code,
                List.of(ActionError.of(code, ActionErrorCategory.DISPATCH, code)));
    }

    private String headerValue(String value) {
        return value != null ? value : "";
    }
}
