package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import org.junit.jupiter.api.RepeatedTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MappingTransformationPropertyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MappingTransformationEngine engine = new MappingTransformationEngine(
            OBJECT_MAPPER, new JsonTreeAccess(), mock(ValueMapService.class), new MappingSecurityValidator());

    @RepeatedTest(40)
    void copyPreservesArbitraryStringValueExactly() {
        String value = UUID.randomUUID() + "-中文-/~-" + System.nanoTime();
        var input = OBJECT_MAPPER.createObjectNode().put("source", value);

        var result = engine.transform(input, List.of(new MappingRuleRequest(
                1, MappingOperation.COPY, "/source", "/nested/target", null, true)));

        assertThat(result.output().at("/nested/target").asText()).isEqualTo(value);
    }
}
