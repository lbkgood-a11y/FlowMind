package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.lowcode.dto.FormFieldSchemaRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LowcodeFormSchemaValidator {

    private static final Set<String> SUPPORTED_FIELD_TYPES = Set.of(
            "string", "number", "integer", "boolean");
    private static final Set<String> SUPPORTED_WIDGETS = Set.of(
            "string", "textarea", "number", "money", "integer",
            "boolean", "enum", "select", "date");
    private static final Set<String> SUPPORTED_FIELD_METADATA_TYPES = Set.of(
            "string", "text", "textarea", "number", "money", "integer",
            "boolean", "enum", "select", "date");
    private static final SchemaRegistry SCHEMA_REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private final ObjectMapper objectMapper;

    public void validate(String schemaJson, String uiSchemaJson, List<FormFieldSchemaRequest> fieldMetadata) {
        if (!StringUtils.hasText(schemaJson)) {
            if (StringUtils.hasText(uiSchemaJson) || (fieldMetadata != null && !fieldMetadata.isEmpty())) {
                throw new BizException(40000, "FORM_SCHEMA_REQUIRED");
            }
            return;
        }

        JsonNode schema = readObject(schemaJson, "INVALID_FORM_SCHEMA");
        try {
            SCHEMA_REGISTRY.getSchema(schemaJson);
        } catch (RuntimeException e) {
            throw new BizException(40000, "INVALID_FORM_SCHEMA");
        }

        if (!"object".equals(schema.path("type").asText())) {
            throw new BizException(40000, "FORM_SCHEMA_ROOT_MUST_BE_OBJECT");
        }
        JsonNode properties = schema.path("properties");
        if (!properties.isObject()) {
            throw new BizException(40000, "FORM_SCHEMA_PROPERTIES_MUST_BE_OBJECT");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            validateField(fields.next().getValue());
        }
        validateRequiredFields(schema.path("required"), properties);
        if (StringUtils.hasText(uiSchemaJson)) {
            validateUiSchema(readObject(uiSchemaJson, "INVALID_FORM_UI_SCHEMA"));
        }
        validateFieldMetadata(properties, fieldMetadata);
    }

    private void validateField(JsonNode fieldSchema) {
        if (!fieldSchema.isObject()) {
            throw new BizException(40000, "INVALID_FORM_FIELD_SCHEMA");
        }
        String type = fieldSchema.path("type").asText();
        if (!SUPPORTED_FIELD_TYPES.contains(type)) {
            throw new BizException(40000, "UNSUPPORTED_FORM_FIELD_TYPE");
        }
        JsonNode enumValues = fieldSchema.get("enum");
        if (enumValues != null && (!enumValues.isArray() || enumValues.isEmpty())) {
            throw new BizException(40000, "INVALID_FORM_ENUM");
        }
        if ("date".equals(fieldSchema.path("format").asText()) && !"string".equals(type)) {
            throw new BizException(40000, "DATE_FIELD_MUST_BE_STRING");
        }
    }

    private void validateRequiredFields(JsonNode required, JsonNode properties) {
        if (required.isMissingNode()) {
            return;
        }
        if (!required.isArray()) {
            throw new BizException(40000, "FORM_REQUIRED_MUST_BE_ARRAY");
        }
        for (JsonNode field : required) {
            if (!field.isTextual() || !properties.has(field.asText())) {
                throw new BizException(40000, "FORM_REQUIRED_FIELD_NOT_FOUND");
            }
        }
    }

    private void validateUiSchema(JsonNode node) {
        if (!node.isObject()) {
            throw new BizException(40000, "INVALID_FORM_UI_SCHEMA");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if ("ui:widget".equals(field.getKey())) {
                if (!field.getValue().isTextual()
                        || !SUPPORTED_WIDGETS.contains(field.getValue().asText())) {
                    throw new BizException(40000, "UNREGISTERED_FORM_WIDGET");
                }
            } else if (field.getValue().isObject()) {
                validateUiSchema(field.getValue());
            }
        }
    }

    private void validateFieldMetadata(JsonNode properties, List<FormFieldSchemaRequest> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (FormFieldSchemaRequest field : fields) {
            if (field == null || !StringUtils.hasText(field.getFieldKey())) {
                throw new BizException(40000, "FORM_FIELD_KEY_REQUIRED");
            }
            String fieldKey = field.getFieldKey().trim();
            if (!seen.add(fieldKey)) {
                throw new BizException(40000, "FORM_FIELD_KEY_DUPLICATE");
            }
            if (!properties.has(fieldKey)) {
                throw new BizException(40000, "FORM_FIELD_NOT_IN_SCHEMA");
            }
            if (StringUtils.hasText(field.getFieldType())
                    && !SUPPORTED_FIELD_METADATA_TYPES.contains(field.getFieldType().trim().toLowerCase())) {
                throw new BizException(40000, "UNSUPPORTED_FORM_FIELD_TYPE");
            }
        }
    }

    private JsonNode readObject(String json, String errorCode) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new BizException(40000, errorCode);
            }
            return node;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(40000, errorCode);
        }
    }
}
