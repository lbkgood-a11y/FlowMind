package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappingDefinitionValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MappingDefinitionValidator validator =
            new MappingDefinitionValidator(new StructureSchemaInspector(), new MappingSecurityValidator());

    @Test
    void reportsCompleteRequiredTargetCoverage() throws Exception {
        var source = OBJECT_MAPPER.readTree("""
                {"type":"object","properties":{"id":{"type":"string"}}}
                """);
        var target = OBJECT_MAPPER.readTree("""
                {"type":"object","required":["externalId"],"properties":{"externalId":{"type":"string"}}}
                """);
        var rules = List.of(new MappingRuleRequest(
                1, MappingOperation.COPY, "/id", "/externalId",
                OBJECT_MAPPER.createObjectNode(), true));

        var result = validator.validate(source, target, rules);

        assertThat(result.path("valid").asBoolean()).isTrue();
        assertThat(result.path("missingRequiredTargets").isEmpty()).isTrue();
    }

    @Test
    void rejectsUnknownSourcePointer() throws Exception {
        var source = OBJECT_MAPPER.readTree("""
                {"type":"object","properties":{}}
                """);
        var target = OBJECT_MAPPER.readTree("""
                {"type":"object","properties":{"externalId":{"type":"string"}}}
                """);
        var rules = List.of(new MappingRuleRequest(
                1, MappingOperation.COPY, "/missing", "/externalId", null, true));

        assertThatThrownBy(() -> validator.validate(source, target, rules))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("SOURCE_PATH_UNKNOWN");
    }

    @Test
    void blocksPublicationWhenRequiredTargetIsUncovered() throws Exception {
        var source = OBJECT_MAPPER.readTree("""
                {"type":"object","properties":{}}
                """);
        var target = OBJECT_MAPPER.readTree("""
                {"type":"object","required":["externalId"],"properties":{"externalId":{"type":"string"}}}
                """);
        var coverage = validator.validate(source, target, List.of());

        assertThatThrownBy(() -> validator.requireCompleteCoverage(coverage))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_MAPPING_REQUIRED_TARGET_COVERAGE_INCOMPLETE");
    }
}
