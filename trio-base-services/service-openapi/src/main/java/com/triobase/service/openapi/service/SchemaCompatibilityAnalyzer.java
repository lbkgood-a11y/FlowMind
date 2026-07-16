package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.service.openapi.domain.enums.SensitivityLevel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SchemaCompatibilityAnalyzer {

    public CompatibilityReport analyze(JsonNode previous, JsonNode candidate, JsonNode semanticChange) {
        Map<String, FieldShape> oldFields = flatten(previous);
        Map<String, FieldShape> newFields = flatten(candidate);
        List<String> breaking = new ArrayList<>();
        List<String> security = new ArrayList<>();
        List<String> compatible = new ArrayList<>();

        oldFields.forEach((path, oldField) -> {
            FieldShape newField = newFields.get(path);
            if (newField == null) {
                breaking.add("FIELD_REMOVED:" + path);
                return;
            }
            if (!oldField.type().equals(newField.type())) {
                breaking.add("TYPE_CHANGED:" + path);
            }
            if (!oldField.required() && newField.required()) {
                breaking.add("FIELD_BECAME_REQUIRED:" + path);
            }
            if ((!oldField.enumValues().isEmpty() && !newField.enumValues().containsAll(oldField.enumValues()))
                    || (oldField.enumValues().isEmpty() && !newField.enumValues().isEmpty())) {
                breaking.add("ENUM_VALUE_REMOVED:" + path);
            }
            if (narrowedMinimum(oldField.minimum(), newField.minimum())
                    || narrowedMaximum(oldField.maximum(), newField.maximum())) {
                breaking.add("CONSTRAINT_NARROWED:" + path);
            }
            if (newField.sensitivity().ordinal() < oldField.sensitivity().ordinal()) {
                security.add("SENSITIVITY_LOWERED:" + path);
            }
            if (!safeEquals(oldField.semanticId(), newField.semanticId())) {
                breaking.add("SEMANTIC_ID_CHANGED:" + path);
            }
        });

        newFields.forEach((path, field) -> {
            if (!oldFields.containsKey(path)) {
                if (field.required()) {
                    breaking.add("REQUIRED_FIELD_ADDED:" + path);
                } else {
                    compatible.add("OPTIONAL_FIELD_ADDED:" + path);
                }
            }
        });

        if (semanticChange != null && semanticChange.path("breaking").asBoolean(false)) {
            breaking.add("DECLARED_SEMANTIC_BREAKING_CHANGE");
        }
        if (semanticChange != null && semanticChange.path("securitySensitive").asBoolean(false)) {
            security.add("DECLARED_SECURITY_SENSITIVE_CHANGE");
        }
        boolean semanticReviewRequired = semanticChange != null && semanticChange.size() > 0;
        return new CompatibilityReport(
                breaking.isEmpty(), !breaking.isEmpty(), !security.isEmpty(), semanticReviewRequired,
                List.copyOf(breaking), List.copyOf(security), List.copyOf(compatible));
    }

    private Map<String, FieldShape> flatten(JsonNode schema) {
        Map<String, FieldShape> fields = new HashMap<>();
        flattenObject(schema, "", fields);
        return fields;
    }

    private void flattenObject(JsonNode schema, String parent, Map<String, FieldShape> fields) {
        Set<String> required = new HashSet<>();
        schema.path("required").forEach(node -> required.add(node.asText()));
        Iterator<Map.Entry<String, JsonNode>> iterator = schema.path("properties").fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> property = iterator.next();
            String path = parent + "/" + property.getKey();
            JsonNode node = property.getValue();
            fields.put(path, new FieldShape(
                    node.path("type").asText("unknown"),
                    required.contains(property.getKey()),
                    node.path("x-triobase-semantic-id").asText(null),
                    sensitivity(node),
                    textSet(node.path("enum")),
                    decimal(node.get("minimum")),
                    decimal(node.get("maximum"))));
            if ("object".equals(node.path("type").asText())) {
                flattenObject(node, path, fields);
            } else if ("array".equals(node.path("type").asText())
                    && "object".equals(node.path("items").path("type").asText())) {
                flattenObject(node.path("items"), path + "/*", fields);
            }
        }
    }

    private SensitivityLevel sensitivity(JsonNode node) {
        try {
            return SensitivityLevel.valueOf(node.path("x-triobase-sensitivity").asText("PUBLIC"));
        } catch (IllegalArgumentException ignored) {
            return SensitivityLevel.RESTRICTED;
        }
    }

    private Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        if (array != null && array.isArray()) {
            array.forEach(value -> values.add(value.asText()));
        }
        return values;
    }

    private BigDecimal decimal(JsonNode value) {
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }

    private boolean narrowedMinimum(BigDecimal oldValue, BigDecimal newValue) {
        return newValue != null && (oldValue == null || newValue.compareTo(oldValue) > 0);
    }

    private boolean narrowedMaximum(BigDecimal oldValue, BigDecimal newValue) {
        return newValue != null && (oldValue == null || newValue.compareTo(oldValue) < 0);
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private record FieldShape(
            String type,
            boolean required,
            String semanticId,
            SensitivityLevel sensitivity,
            Set<String> enumValues,
            BigDecimal minimum,
            BigDecimal maximum) {
    }

    public record CompatibilityReport(
            boolean compatible,
            boolean breaking,
            boolean securitySensitive,
            boolean semanticReviewRequired,
            List<String> breakingReasons,
            List<String> securityReasons,
            List<String> compatibleChanges) {

        public JsonNode toJson() {
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            root.put("compatible", compatible);
            root.put("breaking", breaking);
            root.put("securitySensitive", securitySensitive);
            root.put("semanticReviewRequired", semanticReviewRequired);
            addArray(root, "breakingReasons", breakingReasons);
            addArray(root, "securityReasons", securityReasons);
            addArray(root, "compatibleChanges", compatibleChanges);
            return root;
        }

        private void addArray(ObjectNode root, String name, List<String> values) {
            ArrayNode array = root.putArray(name);
            values.forEach(array::add);
        }
    }
}
