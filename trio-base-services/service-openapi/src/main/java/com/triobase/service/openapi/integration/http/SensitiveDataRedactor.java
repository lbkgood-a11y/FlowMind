package com.triobase.service.openapi.integration.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SensitiveDataRedactor {

    private static final String REDACTED = "***REDACTED***";
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie",
            "x-api-key", "x-signature", "x-vault-token");
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "secret", "token", "accesstoken", "refreshtoken",
            "authorization", "apikey", "signature", "privatekey");

    public Map<String, List<String>> headers(Map<String, List<String>> headers) {
        Map<String, List<String>> sanitized = new LinkedHashMap<>();
        if (headers == null) {
            return sanitized;
        }
        headers.forEach((name, values) -> sanitized.put(name,
                isSensitiveHeader(name) ? List.of(REDACTED) : List.copyOf(values)));
        return sanitized;
    }

    public JsonNode payload(JsonNode payload, JsonNode networkPolicy) {
        if (payload == null) {
            return null;
        }
        JsonNode copy = payload.deepCopy();
        redactKnownFields(copy);
        JsonNode pointers = networkPolicy == null ? null : networkPolicy.get("sensitivePointers");
        if (pointers != null && pointers.isArray()) {
            pointers.forEach(pointer -> redactPointer(copy, pointer.asText()));
        }
        return copy;
    }

    private void redactKnownFields(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalized = field.getKey().replace("-", "").replace("_", "")
                        .toLowerCase(Locale.ROOT);
                if (SENSITIVE_FIELDS.contains(normalized)) {
                    ((ObjectNode) node).put(field.getKey(), REDACTED);
                } else {
                    redactKnownFields(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::redactKnownFields);
        }
    }

    private void redactPointer(JsonNode root, String pointer) {
        if (pointer == null || !pointer.startsWith("/") || pointer.lastIndexOf('/') < 0) {
            return;
        }
        int split = pointer.lastIndexOf('/');
        String parentPointer = split == 0 ? "" : pointer.substring(0, split);
        String field = pointer.substring(split + 1).replace("~1", "/").replace("~0", "~");
        JsonNode parent = parentPointer.isEmpty() ? root : root.at(parentPointer);
        if (parent instanceof ObjectNode objectNode && objectNode.has(field)) {
            objectNode.put(field, REDACTED);
        }
    }

    private boolean isSensitiveHeader(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return SENSITIVE_HEADERS.contains(normalized)
                || normalized.contains("secret") || normalized.contains("token")
                || normalized.contains("signature") || normalized.contains("api-key");
    }
}
