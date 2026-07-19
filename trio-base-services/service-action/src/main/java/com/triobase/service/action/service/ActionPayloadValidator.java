package com.triobase.service.action.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.service.action.exception.ActionRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ActionPayloadValidator {

    private final ObjectMapper objectMapper;

    public List<ActionError> validate(ActionDefinition definition, GlobalActionRequest request) {
        String schemaJson = definition.getPayloadSchemaJson();
        if (schemaJson == null || schemaJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode schema = objectMapper.readTree(schemaJson);
            JsonNode payload = objectMapper.valueToTree(request.getPayload());
            List<ActionError> errors = new ArrayList<>();
            validateNode(schema, payload, "", errors);
            return errors;
        } catch (ActionRuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ActionRuntimeException(
                    40045,
                    ActionErrorCategory.VALIDATION,
                    "ACTION_PAYLOAD_SCHEMA_INVALID",
                    "payloadSchemaJson",
                    exception);
        }
    }

    private void validateNode(JsonNode schema, JsonNode value, String path, List<ActionError> errors) {
        String type = schema.path("type").asText(null);
        if (type != null && !matchesType(type, value)) {
            errors.add(error("ACTION_PAYLOAD_TYPE_MISMATCH",
                    "expected=" + type,
                    display(path)));
            return;
        }
        if ("object".equals(type)) {
            validateObject(schema, value, path, errors);
        } else if ("array".equals(type) && value != null && value.isArray()) {
            JsonNode itemSchema = schema.path("items");
            if (!itemSchema.isMissingNode()) {
                for (int index = 0; index < value.size(); index++) {
                    validateNode(itemSchema, value.get(index), path + "/" + index, errors);
                }
            }
        }
        validateEnum(schema, value, path, errors);
    }

    private void validateObject(JsonNode schema, JsonNode value, String path, List<ActionError> errors) {
        Set<String> required = new HashSet<>();
        schema.path("required").forEach(node -> required.add(node.asText()));
        required.stream()
                .filter(field -> value == null || !value.has(field) || value.get(field).isNull())
                .forEach(field -> errors.add(error("ACTION_PAYLOAD_REQUIRED_MISSING",
                        "required field missing",
                        display(path + "/" + field))));

        Set<String> allowedProperties = new HashSet<>();
        Iterator<Map.Entry<String, JsonNode>> fields = schema.path("properties").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            allowedProperties.add(field.getKey());
            if (value != null && value.has(field.getKey()) && !value.get(field.getKey()).isNull()) {
                validateNode(field.getValue(), value.get(field.getKey()),
                        path + "/" + field.getKey(), errors);
            }
        }
        if (schema.has("additionalProperties")
                && !schema.path("additionalProperties").asBoolean(true)
                && value != null
                && value.isObject()) {
            value.fieldNames().forEachRemaining(field -> {
                if (!allowedProperties.contains(field)) {
                    errors.add(error("ACTION_PAYLOAD_ADDITIONAL_PROPERTY",
                            "unsupported property",
                            display(path + "/" + field)));
                }
            });
        }
    }

    private void validateEnum(JsonNode schema, JsonNode value, String path, List<ActionError> errors) {
        if (!schema.has("enum") || !schema.path("enum").isArray()) {
            return;
        }
        boolean allowed = false;
        for (JsonNode candidate : schema.path("enum")) {
            if (candidate.equals(value)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            errors.add(error("ACTION_PAYLOAD_ENUM_INVALID",
                    "unsupported enum value",
                    display(path)));
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
            default -> true;
        };
    }

    private ActionError error(String code, String message, String field) {
        ActionError error = ActionError.of(code, ActionErrorCategory.VALIDATION, message);
        error.setField(field);
        return error;
    }

    private String display(String path) {
        return path == null || path.isBlank() ? "/" : path;
    }
}
