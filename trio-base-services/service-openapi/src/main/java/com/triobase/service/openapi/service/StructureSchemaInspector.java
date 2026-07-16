package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.SensitivityLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StructureSchemaInspector {

    private static final int INVALID_SCHEMA = 40012;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "object", "array", "string", "integer", "number", "boolean", "null");
    private static final Set<String> CONSTRAINT_KEYS = Set.of(
            "format", "minLength", "maxLength", "minimum", "maximum", "pattern",
            "enum", "minItems", "maxItems", "uniqueItems", "additionalProperties");

    public List<NormalizedField> inspect(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            fail("OPENAPI_SCHEMA_ROOT_MUST_BE_OBJECT");
        }
        String rootType = schema.path("type").asText(null);
        if (!"object".equals(rootType)) {
            fail("OPENAPI_SCHEMA_ROOT_TYPE_MUST_BE_OBJECT");
        }
        List<NormalizedField> fields = new ArrayList<>();
        inspectObject(schema, "", fields);
        return List.copyOf(fields);
    }

    private void inspectObject(JsonNode schema, String parentPointer, List<NormalizedField> fields) {
        JsonNode properties = schema.path("properties");
        if (!properties.isMissingNode() && !properties.isObject()) {
            fail("OPENAPI_SCHEMA_PROPERTIES_MUST_BE_OBJECT:" + parentPointer);
        }
        Set<String> required = readRequired(schema.path("required"), properties, parentPointer);
        Iterator<Map.Entry<String, JsonNode>> iterator = properties.fields();
        int ordinal = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> property = iterator.next();
            String pointer = parentPointer + "/" + escapePointer(property.getKey());
            JsonNode fieldSchema = property.getValue();
            String type = fieldSchema.path("type").asText(null);
            if (type == null || !SUPPORTED_TYPES.contains(type)) {
                fail("OPENAPI_SCHEMA_FIELD_TYPE_UNSUPPORTED:" + pointer);
            }
            SensitivityLevel sensitivity = readSensitivity(fieldSchema, pointer);
            fields.add(new NormalizedField(
                    pointer,
                    property.getKey(),
                    type,
                    required.contains(property.getKey()),
                    "array".equals(type),
                    fieldSchema.path("x-triobase-semantic-id").asText(null),
                    sensitivity,
                    constraints(fieldSchema),
                    ordinal++));
            if ("object".equals(type)) {
                inspectObject(fieldSchema, pointer, fields);
            } else if ("array".equals(type)) {
                inspectArray(fieldSchema, pointer, fields);
            }
        }
    }

    private void inspectArray(JsonNode arraySchema, String pointer, List<NormalizedField> fields) {
        JsonNode items = arraySchema.path("items");
        if (!items.isObject()) {
            fail("OPENAPI_SCHEMA_ARRAY_ITEMS_REQUIRED:" + pointer);
        }
        String itemType = items.path("type").asText(null);
        if (itemType == null || !SUPPORTED_TYPES.contains(itemType)) {
            fail("OPENAPI_SCHEMA_ARRAY_ITEM_TYPE_UNSUPPORTED:" + pointer);
        }
        if ("object".equals(itemType)) {
            inspectObject(items, pointer + "/*", fields);
        } else if ("array".equals(itemType)) {
            inspectArray(items, pointer + "/*", fields);
        }
    }

    private Set<String> readRequired(JsonNode requiredNode, JsonNode properties, String pointer) {
        Set<String> required = new HashSet<>();
        if (requiredNode.isMissingNode()) {
            return required;
        }
        if (!requiredNode.isArray()) {
            fail("OPENAPI_SCHEMA_REQUIRED_MUST_BE_ARRAY:" + pointer);
        }
        requiredNode.forEach(value -> {
            if (!value.isTextual() || !properties.has(value.asText())) {
                fail("OPENAPI_SCHEMA_REQUIRED_FIELD_UNKNOWN:" + pointer);
            }
            required.add(value.asText());
        });
        return required;
    }

    private SensitivityLevel readSensitivity(JsonNode fieldSchema, String pointer) {
        String value = fieldSchema.path("x-triobase-sensitivity").asText("PUBLIC");
        try {
            return SensitivityLevel.valueOf(value);
        } catch (IllegalArgumentException exception) {
            fail("OPENAPI_SCHEMA_SENSITIVITY_INVALID:" + pointer);
            return SensitivityLevel.PUBLIC;
        }
    }

    private JsonNode constraints(JsonNode fieldSchema) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        CONSTRAINT_KEYS.forEach(key -> {
            if (fieldSchema.has(key)) {
                result.set(key, fieldSchema.get(key).deepCopy());
            }
        });
        return result;
    }

    private String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private void fail(String message) {
        throw new BizException(INVALID_SCHEMA, message);
    }

    public record NormalizedField(
            String jsonPointer,
            String fieldName,
            String dataType,
            boolean required,
            boolean array,
            String semanticId,
            SensitivityLevel sensitivity,
            JsonNode constraints,
            int ordinal) {
    }
}
