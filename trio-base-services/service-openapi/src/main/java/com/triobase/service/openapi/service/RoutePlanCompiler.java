package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class RoutePlanCompiler {

    private final ObjectMapper objectMapper;

    public RoutePlanCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompiledRoutePlan compile(RouteVersion route) {
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("routeVersionId", route.getId());
        plan.put("environment", route.getEnvironment().name());
        plan.put("priority", route.getPriority());
        if (route.getEffectiveFrom() != null) {
            plan.put("effectiveFrom", route.getEffectiveFrom().toString());
        }
        if (route.getEffectiveUntil() != null) {
            plan.put("effectiveUntil", route.getEffectiveUntil().toString());
        }
        plan.put("enabled", Boolean.TRUE.equals(route.getEnabled()));
        plan.set("predicate", route.getRoutePredicate() == null
                ? objectMapper.createObjectNode() : route.getRoutePredicate().deepCopy());
        plan.put("executionMode", route.getExecutionMode().name());
        put(plan, "connectorVersionId", route.getConnectorVersionId());
        put(plan, "requestMappingVersionId", route.getRequestMappingVersionId());
        put(plan, "responseMappingVersionId", route.getResponseMappingVersionId());
        put(plan, "orchestrationVersionId", route.getOrchestrationVersionId());
        return new CompiledRoutePlan(plan, hash(plan));
    }

    private void put(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private String hash(JsonNode node) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compile route plan", exception);
        }
    }

    public record CompiledRoutePlan(JsonNode plan, String hash) {
    }
}
