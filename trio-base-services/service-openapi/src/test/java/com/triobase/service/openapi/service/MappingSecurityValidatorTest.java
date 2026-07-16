package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappingSecurityValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MappingSecurityValidator validator = new MappingSecurityValidator();

    @Test
    void acceptsDeclarativeConverterConfig() {
        var config = OBJECT_MAPPER.createObjectNode().put("targetType", "integer");
        assertThatCode(() -> validator.validate(new MappingRuleRequest(
                1, MappingOperation.TYPE_CONVERT, "/amount", "/amount", config, true)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsArbitraryExpression() {
        var config = OBJECT_MAPPER.createObjectNode().put("expression", "${T(java.lang.Runtime)}");
        assertThatThrownBy(() -> validator.validate(new MappingRuleRequest(
                1, MappingOperation.COPY, "/id", "/id", config, true)))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_MAPPING_UNSAFE_EXPRESSION");
    }

    @Test
    void rejectsCredentialConstant() {
        var config = OBJECT_MAPPER.createObjectNode().put("value", "secret-value");
        assertThatThrownBy(() -> validator.validate(new MappingRuleRequest(
                1, MappingOperation.CONSTANT, null, "/authorizationToken", config, true)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("CREDENTIAL_CONSTANT_FORBIDDEN");
    }
}
