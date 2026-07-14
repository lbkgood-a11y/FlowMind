package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormSnapshotValidatorTest {

    private FormSnapshotValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FormSnapshotValidator(new ObjectMapper());
    }

    @Test
    void acceptsRegisteredExpenseFormFields() {
        validator.validate("""
                {
                  "type":"object",
                  "required":["amount","reason"],
                  "properties":{
                    "amount":{"type":"number","minimum":0.01},
                    "reason":{"type":"string"},
                    "approved":{"type":"boolean"},
                    "date":{"type":"string","format":"date"}
                  }
                }
                """, """
                {
                  "amount":{"ui:widget":"money"},
                  "reason":{"ui:widget":"textarea"},
                  "approved":{"ui:widget":"boolean"},
                  "date":{"ui:widget":"date"}
                }
                """);
    }

    @Test
    void rejectsUnknownWidgetAndFieldType() {
        BizException widget = assertThrows(BizException.class, () -> validator.validate(
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}",
                "{\"name\":{\"ui:widget\":\"raw-html\"}}"));
        assertEquals("UNREGISTERED_FORM_WIDGET", widget.getMessage());

        BizException type = assertThrows(BizException.class, () -> validator.validate(
                "{\"type\":\"object\",\"properties\":{\"items\":{\"type\":\"array\"}}}",
                null));
        assertEquals("UNSUPPORTED_FORM_FIELD_TYPE", type.getMessage());
    }

    @Test
    void rejectsRequiredFieldThatIsNotDeclared() {
        BizException exception = assertThrows(BizException.class, () -> validator.validate(
                "{\"type\":\"object\",\"required\":[\"missing\"],\"properties\":{}}",
                null));
        assertEquals("FORM_REQUIRED_FIELD_NOT_FOUND", exception.getMessage());
    }
}
