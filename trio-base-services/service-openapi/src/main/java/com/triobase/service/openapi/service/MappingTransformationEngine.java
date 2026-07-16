package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import com.triobase.service.openapi.dto.TransformationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MappingTransformationEngine {

    private static final int TRANSFORMATION_FAILED = 42221;

    private final ObjectMapper objectMapper;
    private final JsonTreeAccess treeAccess;
    private final ValueMapService valueMapService;
    private final MappingSecurityValidator securityValidator;

    public TransformationResult transform(JsonNode input, List<MappingRuleRequest> rules) {
        if (input == null || !input.isObject()) {
            throw new BizException(TRANSFORMATION_FAILED, "OPENAPI_MAPPING_INPUT_MUST_BE_OBJECT");
        }
        ObjectNode output = objectMapper.createObjectNode();
        List<TransformationResult.RuleTrace> traces = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<MappingRuleRequest> ordered = (rules == null ? List.<MappingRuleRequest>of() : rules)
                .stream().sorted(Comparator.comparingInt(MappingRuleRequest::order)).toList();
        for (MappingRuleRequest rule : ordered) {
            securityValidator.validate(rule);
            try {
                JsonNode value = apply(input, rule, warnings);
                if (value == null) {
                    if (rule.required()) {
                        throw new BizException(
                                TRANSFORMATION_FAILED,
                                "OPENAPI_MAPPING_REQUIRED_SOURCE_MISSING:" + rule.sourcePointer());
                    }
                    traces.add(trace(rule, "SKIPPED"));
                    continue;
                }
                treeAccess.write(output, rule.targetPointer(), value);
                traces.add(trace(rule, "APPLIED"));
            } catch (BizException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new BizException(
                        TRANSFORMATION_FAILED,
                        "OPENAPI_MAPPING_RULE_FAILED:" + rule.order() + ":" + exception.getMessage());
            }
        }
        return new TransformationResult(output, List.copyOf(traces), List.copyOf(warnings));
    }

    private JsonNode apply(JsonNode input, MappingRuleRequest rule, List<String> warnings) {
        return switch (rule.operation()) {
            case COPY, MOVE -> source(input, rule);
            case CONSTANT -> config(rule, "value");
            case DEFAULT -> {
                JsonNode value = source(input, rule);
                yield value == null || value.isNull() ? config(rule, "value") : value;
            }
            case TYPE_CONVERT -> convert(source(input, rule), textConfig(rule, "targetType"));
            case CONCATENATE -> concatenate(input, rule);
            case DATE_FORMAT -> formatDate(source(input, rule), rule);
            case COLLECTION_PROJECT -> projectCollection(source(input, rule), rule, warnings);
            case VALUE_MAP -> mapValue(source(input, rule), rule);
        };
    }

    private JsonNode source(JsonNode input, MappingRuleRequest rule) {
        return treeAccess.read(input, rule.sourcePointer());
    }

    private JsonNode config(MappingRuleRequest rule, String key) {
        JsonNode value = rule.config() == null ? null : rule.config().get(key);
        if (value == null) {
            throw new BizException(TRANSFORMATION_FAILED, "OPENAPI_MAPPING_CONFIG_REQUIRED:" + key);
        }
        return value;
    }

    private String textConfig(MappingRuleRequest rule, String key) {
        JsonNode value = config(rule, key);
        if (!value.isTextual()) {
            throw new BizException(TRANSFORMATION_FAILED, "OPENAPI_MAPPING_TEXT_CONFIG_REQUIRED:" + key);
        }
        return value.asText();
    }

    private JsonNode convert(JsonNode value, String targetType) {
        if (value == null || value.isNull()) {
            return null;
        }
        return switch (targetType.toLowerCase()) {
            case "string" -> JsonNodeFactory.instance.textNode(value.asText());
            case "integer" -> JsonNodeFactory.instance.numberNode(value.isNumber()
                    ? value.longValue() : Long.parseLong(value.asText()));
            case "number" -> JsonNodeFactory.instance.numberNode(value.isNumber()
                    ? value.decimalValue() : new BigDecimal(value.asText()));
            case "boolean" -> JsonNodeFactory.instance.booleanNode(value.isBoolean()
                    ? value.booleanValue() : Boolean.parseBoolean(value.asText()));
            default -> throw new BizException(
                    TRANSFORMATION_FAILED, "OPENAPI_MAPPING_TARGET_TYPE_UNSUPPORTED:" + targetType);
        };
    }

    private JsonNode concatenate(JsonNode input, MappingRuleRequest rule) {
        JsonNode sources = config(rule, "sources");
        String delimiter = rule.config().path("delimiter").asText("");
        List<String> values = new ArrayList<>();
        sources.forEach(pointer -> {
            JsonNode value = treeAccess.read(input, pointer.asText());
            if (value != null && !value.isNull()) {
                values.add(value.asText());
            }
        });
        return JsonNodeFactory.instance.textNode(String.join(delimiter, values));
    }

    private JsonNode formatDate(JsonNode value, MappingRuleRequest rule) {
        if (value == null || value.isNull()) {
            return null;
        }
        DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern(textConfig(rule, "inputPattern"));
        DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern(textConfig(rule, "outputPattern"));
        String text = value.asText();
        try {
            return JsonNodeFactory.instance.textNode(LocalDateTime.parse(text, inputFormat).format(outputFormat));
        } catch (Exception ignored) {
            return JsonNodeFactory.instance.textNode(LocalDate.parse(text, inputFormat).format(outputFormat));
        }
    }

    private JsonNode projectCollection(
            JsonNode source,
            MappingRuleRequest rule,
            List<String> warnings) {
        if (source == null || source.isNull()) {
            return null;
        }
        if (!source.isArray()) {
            throw new BizException(TRANSFORMATION_FAILED, "OPENAPI_MAPPING_COLLECTION_SOURCE_NOT_ARRAY");
        }
        JsonNode fields = config(rule, "fields");
        if (!fields.isObject()) {
            throw new BizException(TRANSFORMATION_FAILED, "OPENAPI_MAPPING_COLLECTION_FIELDS_REQUIRED");
        }
        ArrayNode output = objectMapper.createArrayNode();
        for (JsonNode item : source) {
            ObjectNode projected = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> mappings = fields.fields();
            while (mappings.hasNext()) {
                Map.Entry<String, JsonNode> mapping = mappings.next();
                JsonNode value = treeAccess.read(item, mapping.getKey());
                if (value == null) {
                    warnings.add("COLLECTION_FIELD_MISSING:" + mapping.getKey());
                    continue;
                }
                treeAccess.write(projected, mapping.getValue().asText(), value);
            }
            output.add(projected);
        }
        return output;
    }

    private JsonNode mapValue(JsonNode value, MappingRuleRequest rule) {
        if (value == null || value.isNull()) {
            return null;
        }
        String versionId = textConfig(rule, "valueMapVersionId");
        boolean canonicalToExternal = rule.config().path("canonicalToExternal").asBoolean(true);
        return JsonNodeFactory.instance.textNode(
                valueMapService.lookup(versionId, value.asText(), canonicalToExternal));
    }

    private TransformationResult.RuleTrace trace(MappingRuleRequest rule, String outcome) {
        return new TransformationResult.RuleTrace(
                rule.order(), rule.operation().name(), rule.sourcePointer(), rule.targetPointer(), outcome);
    }
}
