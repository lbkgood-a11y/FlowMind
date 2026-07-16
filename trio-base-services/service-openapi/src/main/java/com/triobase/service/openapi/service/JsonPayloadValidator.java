package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JsonPayloadValidator {

    public ValidationResult validate(JsonNode schema, JsonNode payload) {
        List<String> errors = new ArrayList<>();
        validateNode(schema, payload, "", errors);
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors));
    }

    private void validateNode(JsonNode schema, JsonNode value, String path, List<String> errors) {
        String type = schema.path("type").asText(null);
        if (type != null && !matchesType(type, value)) {
            errors.add("TYPE_MISMATCH:" + display(path) + ":expected=" + type);
            return;
        }
        if ("object".equals(type)) {
            Set<String> required = new HashSet<>();
            schema.path("required").forEach(node -> required.add(node.asText()));
            required.stream().filter(field -> !value.has(field) || value.get(field).isNull())
                    .forEach(field -> errors.add("REQUIRED_FIELD_MISSING:" + display(path + "/" + field)));
            Iterator<Map.Entry<String, JsonNode>> fields = schema.path("properties").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (value.has(field.getKey()) && !value.get(field.getKey()).isNull()) {
                    validateNode(field.getValue(), value.get(field.getKey()),
                            path + "/" + field.getKey(), errors);
                }
            }
        } else if ("array".equals(type) && value.isArray()) {
            JsonNode itemSchema = schema.path("items");
            for (int index = 0; index < value.size(); index++) {
                validateNode(itemSchema, value.get(index), path + "/" + index, errors);
            }
        }
        if (schema.has("enum") && schema.path("enum").isArray()) {
            boolean allowed = false;
            for (JsonNode candidate : schema.path("enum")) {
                if (candidate.equals(value)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                errors.add("ENUM_VALUE_INVALID:" + display(path));
            }
        }
    }

    private boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value != null && value.isObject();
            case "array" -> value != null && value.isArray();
            case "string" -> value != null && value.isTextual();
            case "integer" -> value != null && value.isIntegralNumber();
            case "number" -> value != null && value.isNumber();
            case "boolean" -> value != null && value.isBoolean();
            case "null" -> value == null || value.isNull();
            default -> false;
        };
    }

    private String display(String path) {
        return path.isBlank() ? "/" : path;
    }

    public record ValidationResult(boolean valid, List<String> errors) {
    }
}
