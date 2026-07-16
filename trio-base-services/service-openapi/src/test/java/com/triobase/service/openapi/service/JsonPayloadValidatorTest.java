package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPayloadValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final JsonPayloadValidator validator = new JsonPayloadValidator();

    @Test
    void reportsRequiredTypeAndEnumErrors() throws Exception {
        var schema = OBJECT_MAPPER.readTree("""
                {
                  "type":"object",
                  "required":["id","status"],
                  "properties":{
                    "id":{"type":"integer"},
                    "status":{"type":"string","enum":["OPEN","CLOSED"]}
                  }
                }
                """);
        var payload = OBJECT_MAPPER.readTree("{\"id\":\"42\",\"status\":\"UNKNOWN\"}");

        var result = validator.validate(schema, payload);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("TYPE_MISMATCH:/id:expected=integer", "ENUM_VALUE_INVALID:/status");
    }
}
