package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.MappingContractTest;
import com.triobase.service.openapi.dto.TransformationResult;
import com.triobase.service.openapi.infrastructure.mapper.MappingContractTestMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MappingContractTestServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Mock private MappingContractTestMapper contractTestMapper;
    @Mock private MappingVersionMapper mappingVersionMapper;
    @Mock private MappingTransformationEngine engine;
    private MappingContractTestService service;

    @BeforeEach
    void setUp() {
        service = new MappingContractTestService(contractTestMapper, mappingVersionMapper, engine);
    }

    @Test
    void passesMatchingRequiredContractTest() throws Exception {
        MappingContractTest test = test(true);
        when(contractTestMapper.selectList(any(Wrapper.class))).thenReturn(List.of(test));
        when(engine.transform(any(), any())).thenReturn(new TransformationResult(
                OBJECT_MAPPER.readTree("{\"id\":\"42\"}"), List.of(), List.of()));

        var result = service.run("mapping-v1", List.of());

        assertThat(result.passed()).isTrue();
        assertThat(result.executed()).isEqualTo(1);
    }

    @Test
    void blocksPublicationWhenRequiredTestFails() throws Exception {
        MappingContractTest test = test(true);
        when(contractTestMapper.selectList(any(Wrapper.class))).thenReturn(List.of(test));
        when(engine.transform(any(), any())).thenReturn(new TransformationResult(
                OBJECT_MAPPER.readTree("{\"id\":\"wrong\"}"), List.of(), List.of()));

        var result = service.run("mapping-v1", List.of());

        assertThat(result.passed()).isFalse();
        assertThatThrownBy(() -> service.requirePassing(result))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_MAPPING_REQUIRED_CONTRACT_TEST_FAILED");
    }

    private MappingContractTest test(boolean required) throws Exception {
        MappingContractTest test = new MappingContractTest();
        test.setId("test-1");
        test.setTestName("maps id");
        test.setInputPayload(OBJECT_MAPPER.readTree("{\"sourceId\":\"42\"}"));
        test.setExpectedOutput(OBJECT_MAPPER.readTree("{\"id\":\"42\"}"));
        test.setRequiredTest(required);
        return test;
    }
}
