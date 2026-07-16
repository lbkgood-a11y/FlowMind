package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.MappingRule;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.SensitivityLevel;
import com.triobase.service.openapi.dto.MappingPreviewResponse;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import com.triobase.service.openapi.dto.TransformationResult;
import com.triobase.service.openapi.infrastructure.mapper.MappingRuleMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MappingPreviewService {

    private final MappingVersionMapper mappingVersionMapper;
    private final MappingRuleMapper mappingRuleMapper;
    private final StructureVersionMapper structureVersionMapper;
    private final MappingTransformationEngine transformationEngine;
    private final JsonPayloadValidator payloadValidator;
    private final StructureSchemaInspector schemaInspector;

    public MappingPreviewResponse preview(String mappingVersionId, JsonNode payload) {
        MappingVersion mapping = mappingVersionMapper.selectById(mappingVersionId);
        if (mapping == null) {
            throw new BizException(40421, "OPENAPI_MAPPING_VERSION_NOT_FOUND");
        }
        StructureVersion source = requireStructureVersion(mapping.getSourceStructureVersionId());
        StructureVersion target = requireStructureVersion(mapping.getTargetStructureVersionId());
        JsonPayloadValidator.ValidationResult sourceValidation =
                payloadValidator.validate(source.getSchemaContent(), payload);
        if (!sourceValidation.valid()) {
            return new MappingPreviewResponse(
                    null, List.of(), List.of(), sourceValidation.errors(), List.of());
        }
        TransformationResult result = transformationEngine.transform(payload, loadRules(mappingVersionId));
        JsonPayloadValidator.ValidationResult targetValidation =
                payloadValidator.validate(target.getSchemaContent(), result.output());
        JsonNode redacted = redact(result.output(), schemaInspector.inspect(target.getSchemaContent()));
        return new MappingPreviewResponse(
                redacted, result.traces(), result.warnings(), List.of(), targetValidation.errors());
    }

    private StructureVersion requireStructureVersion(String id) {
        StructureVersion version = structureVersionMapper.selectById(id);
        if (version == null) {
            throw new BizException(40411, "OPENAPI_STRUCTURE_VERSION_NOT_FOUND");
        }
        return version;
    }

    private List<MappingRuleRequest> loadRules(String mappingVersionId) {
        return mappingRuleMapper.selectList(new LambdaQueryWrapper<MappingRule>()
                        .eq(MappingRule::getMappingVersionId, mappingVersionId)
                        .orderByAsc(MappingRule::getRuleOrder))
                .stream().sorted(Comparator.comparing(MappingRule::getRuleOrder))
                .map(rule -> new MappingRuleRequest(
                        rule.getRuleOrder(), rule.getOperationType(), rule.getSourcePointer(),
                        rule.getTargetPointer(), rule.getOperationConfig(),
                        Boolean.TRUE.equals(rule.getRequiredRule())))
                .toList();
    }

    private JsonNode redact(
            JsonNode output,
            List<StructureSchemaInspector.NormalizedField> fields) {
        JsonNode copy = output.deepCopy();
        fields.stream()
                .filter(field -> field.sensitivity() == SensitivityLevel.SENSITIVE
                        || field.sensitivity() == SensitivityLevel.RESTRICTED)
                .forEach(field -> redactPointer(copy, field.jsonPointer().substring(1).split("/"), 0));
        return copy;
    }

    private void redactPointer(JsonNode node, String[] segments, int index) {
        if (node == null || index >= segments.length) {
            return;
        }
        String segment = segments[index].replace("~1", "/").replace("~0", "~");
        if ("*".equals(segment)) {
            if (node.isArray()) {
                node.forEach(item -> redactPointer(item, segments, index + 1));
            }
            return;
        }
        if (index == segments.length - 1 && node.isObject() && node.has(segment)) {
            ((ObjectNode) node).put(segment, "***REDACTED***");
            return;
        }
        redactPointer(node.get(segment), segments, index + 1);
    }
}
