package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MappingDefinitionValidator {

    private static final int INVALID_MAPPING = 40020;
    private static final Set<MappingOperation> SOURCE_REQUIRED = Set.of(
            MappingOperation.COPY, MappingOperation.MOVE, MappingOperation.TYPE_CONVERT,
            MappingOperation.DATE_FORMAT, MappingOperation.COLLECTION_PROJECT,
            MappingOperation.VALUE_MAP);

    private final StructureSchemaInspector schemaInspector;
    private final MappingSecurityValidator securityValidator;

    public MappingDefinitionValidator(
            StructureSchemaInspector schemaInspector,
            MappingSecurityValidator securityValidator) {
        this.schemaInspector = schemaInspector;
        this.securityValidator = securityValidator;
    }

    public JsonNode validate(JsonNode sourceSchema, JsonNode targetSchema, List<MappingRuleRequest> rules) {
        Map<String, StructureSchemaInspector.NormalizedField> sourceFields = byPointer(
                schemaInspector.inspect(sourceSchema));
        Map<String, StructureSchemaInspector.NormalizedField> targetFields = byPointer(
                schemaInspector.inspect(targetSchema));
        Set<Integer> orders = new HashSet<>();
        Set<String> mappedTargets = new HashSet<>();
        for (MappingRuleRequest rule : rules == null ? List.<MappingRuleRequest>of() : rules) {
            securityValidator.validate(rule);
            if (!orders.add(rule.order())) {
                fail("OPENAPI_MAPPING_RULE_ORDER_DUPLICATE:" + rule.order());
            }
            if (rule.operation() == null || !StringUtils.hasText(rule.targetPointer())) {
                fail("OPENAPI_MAPPING_RULE_INVALID:" + rule.order());
            }
            if (!targetFields.containsKey(rule.targetPointer())) {
                fail("OPENAPI_MAPPING_TARGET_PATH_UNKNOWN:" + rule.targetPointer());
            }
            if (SOURCE_REQUIRED.contains(rule.operation())) {
                requireSourcePath(rule, sourceFields);
            }
            if (rule.operation() == MappingOperation.CONCATENATE) {
                validateConcatenateSources(rule, sourceFields);
            }
            mappedTargets.add(rule.targetPointer());
        }

        ArrayNode missing = JsonNodeFactory.instance.arrayNode();
        targetFields.values().stream()
                .filter(StructureSchemaInspector.NormalizedField::required)
                .filter(field -> !mappedTargets.contains(field.jsonPointer()))
                .map(StructureSchemaInspector.NormalizedField::jsonPointer)
                .sorted()
                .forEach(missing::add);
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("valid", missing.isEmpty());
        result.put("requiredTargetCount", targetFields.values().stream()
                .filter(StructureSchemaInspector.NormalizedField::required).count());
        result.put("mappedTargetCount", mappedTargets.size());
        result.set("missingRequiredTargets", missing);
        return result;
    }

    public void requireCompleteCoverage(JsonNode coverage) {
        if (coverage == null || !coverage.path("valid").asBoolean(false)) {
            throw new BizException(40920, "OPENAPI_MAPPING_REQUIRED_TARGET_COVERAGE_INCOMPLETE");
        }
    }

    private void requireSourcePath(
            MappingRuleRequest rule,
            Map<String, StructureSchemaInspector.NormalizedField> sourceFields) {
        if (!StringUtils.hasText(rule.sourcePointer()) || !sourceFields.containsKey(rule.sourcePointer())) {
            fail("OPENAPI_MAPPING_SOURCE_PATH_UNKNOWN:" + rule.sourcePointer());
        }
    }

    private void validateConcatenateSources(
            MappingRuleRequest rule,
            Map<String, StructureSchemaInspector.NormalizedField> sourceFields) {
        JsonNode sources = rule.config() == null ? null : rule.config().get("sources");
        if (sources == null || !sources.isArray() || sources.isEmpty()) {
            fail("OPENAPI_MAPPING_CONCATENATE_SOURCES_REQUIRED:" + rule.order());
        }
        sources.forEach(source -> {
            if (!source.isTextual() || !sourceFields.containsKey(source.asText())) {
                fail("OPENAPI_MAPPING_SOURCE_PATH_UNKNOWN:" + source.asText());
            }
        });
    }

    private Map<String, StructureSchemaInspector.NormalizedField> byPointer(
            List<StructureSchemaInspector.NormalizedField> fields) {
        Map<String, StructureSchemaInspector.NormalizedField> result = new HashMap<>();
        fields.forEach(field -> result.put(field.jsonPointer(), field));
        return result;
    }

    private void fail(String message) {
        throw new BizException(INVALID_MAPPING, message);
    }
}
