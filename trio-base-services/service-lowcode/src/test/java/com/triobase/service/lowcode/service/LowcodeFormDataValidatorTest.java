package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.lowcode.dto.FormFieldValidationError;
import com.triobase.service.lowcode.exception.FormDataValidationException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LowcodeFormDataValidatorTest {

    private final LowcodeFormDataValidator validator = new LowcodeFormDataValidator(new ObjectMapper());

    @Test
    void acceptsValidFormData() {
        assertDoesNotThrow(() -> validator.validate(schemaJson(), Map.of(
                "amount", 99.5,
                "reason", "team dinner")));
    }

    @Test
    void reportsStructuredFieldErrors() {
        FormDataValidationException exception = assertThrows(FormDataValidationException.class,
                () -> validator.validate(schemaJson(), Map.of(
                        "amount", -1,
                        "extra", "not allowed")));

        Map<String, FormFieldValidationError> errors = exception.getFieldErrors().stream()
                .collect(Collectors.toMap(FormFieldValidationError::field, Function.identity()));
        assertEquals("OUT_OF_RANGE", errors.get("amount").code());
        assertEquals("REQUIRED", errors.get("reason").code());
        assertEquals("UNKNOWN_FIELD", errors.get("extra").code());
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
