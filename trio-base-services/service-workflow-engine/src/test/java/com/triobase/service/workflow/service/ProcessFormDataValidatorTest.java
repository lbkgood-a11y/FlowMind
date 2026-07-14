package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.workflow.dto.FormFieldValidationError;
import com.triobase.service.workflow.exception.FormDataValidationException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessFormDataValidatorTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "required": ["reason"],
              "additionalProperties": false,
              "properties": {
                "reason": {"type": "string", "minLength": 2},
                "amount": {"type": "number", "minimum": 0.01},
                "count": {"type": "integer", "minimum": 1}
              }
            }
            """;

    private final ProcessFormDataValidator validator =
            new ProcessFormDataValidator(new ObjectMapper());

    @Test
    void returnsStructuredErrorsForMissingTypeRangeAndUnknownFields() {
        FormDataValidationException exception = assertThrows(FormDataValidationException.class,
                () -> validator.validate(SCHEMA, Map.of(
                        "amount", "not-a-number",
                        "count", 0,
                        "unexpected", true)));

        Map<String, FormFieldValidationError> errors = exception.getFieldErrors().stream()
                .collect(Collectors.toMap(FormFieldValidationError::field, Function.identity()));

        assertEquals("REQUIRED", errors.get("reason").code());
        assertEquals("TYPE_MISMATCH", errors.get("amount").code());
        assertEquals("OUT_OF_RANGE", errors.get("count").code());
        assertEquals("UNKNOWN_FIELD", errors.get("unexpected").code());
    }

    @Test
    void acceptsValidFormDataAndEmptySchema() {
        assertDoesNotThrow(() -> validator.validate(SCHEMA, Map.of(
                "reason", "Taxi",
                "amount", 88.5,
                "count", 1)));
        assertDoesNotThrow(() -> validator.validate(null, Map.of("anything", true)));
    }
}
