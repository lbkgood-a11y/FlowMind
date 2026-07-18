package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.lowcode.dto.FormFieldSchemaRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LowcodeFormSchemaValidatorTest {

    private final LowcodeFormSchemaValidator validator = new LowcodeFormSchemaValidator(new ObjectMapper());

    @Test
    void acceptsSupportedSchemaWidgetsAndHistoricalFieldMetadataTypes() {
        FormFieldSchemaRequest reason = new FormFieldSchemaRequest();
        reason.setFieldKey("reason");
        reason.setFieldType("textarea");

        assertDoesNotThrow(() -> validator.validate(schemaJson(), """
                {"amount":{"ui:widget":"money"},"reason":{"ui:widget":"textarea"}}
                """, List.of(reason)));
    }

    @Test
    void rejectsUnregisteredWidget() {
        BizException exception = assertThrows(BizException.class,
                () -> validator.validate(schemaJson(), "{\"reason\":{\"ui:widget\":\"raw-html\"}}", null));

        assertEquals("UNREGISTERED_FORM_WIDGET", exception.getMessage());
    }

    @Test
    void rejectsRequiredFieldMissingFromProperties() {
        BizException exception = assertThrows(BizException.class,
                () -> validator.validate("""
                        {"type":"object","required":["missing"],"properties":{"reason":{"type":"string"}}}
                        """, null, null));

        assertEquals("FORM_REQUIRED_FIELD_NOT_FOUND", exception.getMessage());
    }

    private String schemaJson() {
        return """
                {
                  "type":"object",
                  "additionalProperties":false,
                  "required":["amount","reason"],
                  "properties":{
                    "amount":{"type":"number","minimum":0.01},
                    "reason":{"type":"string","minLength":2}
                  }
                }
                """;
    }
}
