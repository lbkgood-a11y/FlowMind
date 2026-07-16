package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MappingPlanCompilerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MappingPlanCompiler compiler = new MappingPlanCompiler(objectMapper);

    @Test
    void compilesDeterministicOrderedSerializablePlan() {
        MappingVersion version = new MappingVersion();
        version.setId("mapping-v1");
        version.setSourceStructureVersionId("source-v1");
        version.setTargetStructureVersionId("target-v1");
        var plan = compiler.compile(version, List.of(
                new MappingRuleRequest(2, MappingOperation.CONSTANT, null, "/source",
                        objectMapper.createObjectNode().put("value", "TRIOBASE"), true),
                new MappingRuleRequest(1, MappingOperation.COPY, "/id", "/id", null, true)));

        assertThat(plan.hash()).hasSize(64);
        assertThat(plan.plan().at("/rules/0/order").asInt()).isEqualTo(1);
        assertThat(compiler.deserialize(plan.serialized())).isEqualTo(plan.plan());
    }
}
