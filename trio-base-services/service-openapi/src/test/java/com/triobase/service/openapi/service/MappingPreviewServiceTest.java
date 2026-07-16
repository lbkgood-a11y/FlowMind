package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.openapi.domain.entity.MappingRule;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import com.triobase.service.openapi.dto.TransformationResult;
import com.triobase.service.openapi.infrastructure.mapper.MappingRuleMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MappingPreviewServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Mock private MappingVersionMapper mappingVersionMapper;
    @Mock private MappingRuleMapper mappingRuleMapper;
    @Mock private StructureVersionMapper structureVersionMapper;
    @Mock private MappingTransformationEngine transformationEngine;
    private MappingPreviewService service;

    @BeforeEach
    void setUp() {
        service = new MappingPreviewService(
                mappingVersionMapper, mappingRuleMapper, structureVersionMapper,
                transformationEngine, new JsonPayloadValidator(), new StructureSchemaInspector());
    }

    @Test
    void validatesTransformsAndRedactsSensitiveOutput() throws Exception {
        MappingVersion mapping = new MappingVersion();
        mapping.setId("mapping-v1");
        mapping.setSourceStructureVersionId("source-v1");
        mapping.setTargetStructureVersionId("target-v1");
        when(mappingVersionMapper.selectById("mapping-v1")).thenReturn(mapping);
        when(structureVersionMapper.selectById("source-v1")).thenReturn(version(
                "{\"type\":\"object\",\"required\":[\"phone\"],\"properties\":{\"phone\":{\"type\":\"string\"}}}"));
        when(structureVersionMapper.selectById("target-v1")).thenReturn(version(
                "{\"type\":\"object\",\"required\":[\"mobile\"],\"properties\":{\"mobile\":{\"type\":\"string\",\"x-triobase-sensitivity\":\"SENSITIVE\"}}}"));
        MappingRule rule = new MappingRule();
        rule.setRuleOrder(1);
        rule.setOperationType(MappingOperation.COPY);
        rule.setSourcePointer("/phone");
        rule.setTargetPointer("/mobile");
        rule.setRequiredRule(true);
        when(mappingRuleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rule));
        var transformed = OBJECT_MAPPER.readTree("{\"mobile\":\"13800000000\"}");
        when(transformationEngine.transform(any(), any())).thenReturn(new TransformationResult(
                transformed,
                List.of(new TransformationResult.RuleTrace(1, "COPY", "/phone", "/mobile", "APPLIED")),
                List.of()));

        var response = service.preview("mapping-v1", OBJECT_MAPPER.readTree("{\"phone\":\"13800000000\"}"));

        assertThat(response.sourceValidationErrors()).isEmpty();
        assertThat(response.targetValidationErrors()).isEmpty();
        assertThat(response.output().path("mobile").asText()).isEqualTo("***REDACTED***");
    }

    private StructureVersion version(String schema) throws Exception {
        StructureVersion version = new StructureVersion();
        version.setSchemaContent(OBJECT_MAPPER.readTree(schema));
        return version;
    }
}
