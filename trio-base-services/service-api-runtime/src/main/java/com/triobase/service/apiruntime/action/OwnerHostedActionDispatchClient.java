package com.triobase.service.apiruntime.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.core.trace.TraceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class OwnerHostedActionDispatchClient {

    private final RestTemplate restTemplate;
    private final OwnerActionDispatchProperties properties;
    private final ObjectMapper objectMapper;

    public GlobalActionResult dispatch(GlobalActionRequest request) {
        String ownerService = request != null && request.getTarget() != null
                ? request.getTarget().getOwnerService() : null;
        OwnerActionDispatchProperties.Endpoint endpoint = properties.endpoint(ownerService)
                .orElseThrow(() -> new OwnerActionDispatchException(false, null,
                        "OPENAPI_OWNER_ACTION_OWNER_NOT_CONFIGURED"));
        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    endpoint.dispatchUrl(), new HttpEntity<>(request, headers(request)), JsonNode.class);
            return parseResponse(response.getStatusCode().value(), response.getBody());
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new OwnerActionDispatchException(retryable(status), status,
                    "OPENAPI_OWNER_ACTION_HTTP_" + status);
        } catch (ResourceAccessException exception) {
            throw new OwnerActionDispatchException(true, null,
                    "OPENAPI_OWNER_ACTION_TRANSPORT_FAILURE");
        } catch (RestClientException exception) {
            throw new OwnerActionDispatchException(true, null,
                    "OPENAPI_OWNER_ACTION_CLIENT_FAILURE");
        }
    }

    private HttpHeaders headers(GlobalActionRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String traceId = request != null && request.getContext() != null
                ? request.getContext().getTraceId() : null;
        headers.set(TraceUtil.TRACE_ID_KEY, StringUtils.hasText(traceId) ? traceId : "unknown");
        return headers;
    }

    private GlobalActionResult parseResponse(int status, JsonNode body) {
        if (body == null || body.isNull()) {
            throw new OwnerActionDispatchException(retryable(status), status,
                    "OPENAPI_OWNER_ACTION_EMPTY_RESPONSE");
        }
        if (body.has("code") && body.path("code").asInt() != 0) {
            throw new OwnerActionDispatchException(false, status,
                    StringUtils.hasText(body.path("message").asText())
                            ? body.path("message").asText() : "OPENAPI_OWNER_ACTION_REJECTED");
        }
        JsonNode data = body.has("data") ? body.path("data") : body;
        if (data.isMissingNode() || data.isNull()) {
            throw new OwnerActionDispatchException(false, status,
                    "OPENAPI_OWNER_ACTION_RESULT_MISSING");
        }
        return objectMapper.convertValue(data, GlobalActionResult.class);
    }

    private boolean retryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }
}
