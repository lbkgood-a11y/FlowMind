package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.lowcode.dto.FormFieldValidationError;
import com.triobase.service.lowcode.exception.FormDataValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LowcodeFormDataValidator {

    private static final SchemaRegistry SCHEMA_REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private final ObjectMapper objectMapper;

    public void validate(String schemaJson, Map<String, Object> formData) {
        if (!StringUtils.hasText(schemaJson)) {
            return;
        }

        JsonNode dataNode = objectMapper.valueToTree(formData == null ? Map.of() : formData);
        List<Error> errors;
        try {
            Schema schema = SCHEMA_REGISTRY.getSchema(schemaJson);
            errors = schema.validate(dataNode);
        } catch (RuntimeException exception) {
            throw new BizException(50013, "INVALID_FORM_SCHEMA_SNAPSHOT");
        }

        if (!errors.isEmpty()) {
            List<FormFieldValidationError> fieldErrors = errors.stream()
                    .map(this::toFieldError)
                    .sorted(Comparator.comparing(FormFieldValidationError::field)
                            .thenComparing(FormFieldValidationError::code))
                    .toList();
            throw new FormDataValidationException(fieldErrors);
        }
    }

    private FormFieldValidationError toFieldError(Error error) {
        String keyword = error.getKeyword();
        String field = normalizePath(error.getInstanceLocation().toString());
        if (("required".equals(keyword) || "additionalProperties".equals(keyword))
                && StringUtils.hasText(error.getProperty())) {
            field = appendField(field, error.getProperty());
        }
        return new FormFieldValidationError(
                field,
                errorCode(keyword),
                error.getMessage(),
                keyword);
    }

    private String errorCode(String keyword) {
        return switch (keyword) {
            case "required" -> "REQUIRED";
            case "type" -> "TYPE_MISMATCH";
            case "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
                    "minLength", "maxLength", "minItems", "maxItems" -> "OUT_OF_RANGE";
            case "additionalProperties", "unevaluatedProperties" -> "UNKNOWN_FIELD";
            default -> "INVALID_VALUE";
        };
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path) || "$".equals(path) || "/".equals(path)) {
            return "";
        }
        if (path.startsWith("$.")) {
            return path.substring(2);
        }
        if (path.startsWith("/")) {
            return path.substring(1).replace('/', '.');
        }
        return path;
    }

    private String appendField(String parent, String child) {
        return StringUtils.hasText(parent) ? parent + "." + child : child;
    }
}
