package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MappingTransformationEngineTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Mock private ValueMapService valueMapService;
    private MappingTransformationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MappingTransformationEngine(
                OBJECT_MAPPER, new JsonTreeAccess(), valueMapService, new MappingSecurityValidator());
    }

    @Test
    void appliesCopyConstantConvertConcatenateAndValueMap() throws Exception {
        when(valueMapService.lookup("status-map-v1", "OPEN", true)).thenReturn("O");
        var input = OBJECT_MAPPER.readTree("""
                {"id":"42","firstName":"Ada","lastName":"Lovelace","status":"OPEN"}
                """);
        var rules = List.of(
                rule(1, MappingOperation.COPY, "/id", "/external/id", null, true),
                rule(2, MappingOperation.CONSTANT, null, "/source",
                        OBJECT_MAPPER.createObjectNode().put("value", "TRIOBASE"), true),
                rule(3, MappingOperation.TYPE_CONVERT, "/id", "/external/numericId",
                        OBJECT_MAPPER.createObjectNode().put("targetType", "integer"), true),
                rule(4, MappingOperation.CONCATENATE, null, "/fullName",
                        OBJECT_MAPPER.createObjectNode()
                                .set("sources", OBJECT_MAPPER.createArrayNode().add("/firstName").add("/lastName")),
                        true),
                rule(5, MappingOperation.VALUE_MAP, "/status", "/statusCode",
                        OBJECT_MAPPER.createObjectNode()
                                .put("valueMapVersionId", "status-map-v1")
                                .put("canonicalToExternal", true), true));
        ((com.fasterxml.jackson.databind.node.ObjectNode) rules.get(3).config()).put("delimiter", " ");

        var result = engine.transform(input, rules);

        assertThat(result.output().at("/external/id").asText()).isEqualTo("42");
        assertThat(result.output().at("/external/numericId").asInt()).isEqualTo(42);
        assertThat(result.output().path("fullName").asText()).isEqualTo("Ada Lovelace");
        assertThat(result.output().path("statusCode").asText()).isEqualTo("O");
        assertThat(result.traces()).hasSize(5);
    }

    @Test
    void projectsCollectionFields() throws Exception {
        var input = OBJECT_MAPPER.readTree("""
                {"items":[{"sku":"A","qty":2},{"sku":"B","qty":3}]}
                """);
        var fields = OBJECT_MAPPER.createObjectNode().put("/sku", "/code").put("/qty", "/quantity");
        var config = OBJECT_MAPPER.createObjectNode().set("fields", fields);

        var result = engine.transform(input, List.of(rule(
                1, MappingOperation.COLLECTION_PROJECT, "/items", "/lines", config, true)));

        assertThat(result.output().at("/lines/0/code").asText()).isEqualTo("A");
        assertThat(result.output().at("/lines/1/quantity").asInt()).isEqualTo(3);
    }

    @Test
    void failsWhenRequiredSourceIsMissing() throws Exception {
        var input = OBJECT_MAPPER.readTree("{}");
        assertThatThrownBy(() -> engine.transform(input, List.of(rule(
                1, MappingOperation.COPY, "/missing", "/target", null, true))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("REQUIRED_SOURCE_MISSING");
    }

    private MappingRuleRequest rule(
            int order,
            MappingOperation operation,
            String source,
            String target,
            com.fasterxml.jackson.databind.JsonNode config,
            boolean required) {
        return new MappingRuleRequest(order, operation, source, target, config, required);
    }
}
