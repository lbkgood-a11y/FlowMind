package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.SensitivityLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructureSchemaInspectorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final StructureSchemaInspector inspector = new StructureSchemaInspector();

    @Test
    void extractsNestedAndArrayFields() throws Exception {
        var schema = OBJECT_MAPPER.readTree("""
                {
                  "type":"object",
                  "required":["customer"],
                  "properties":{
                    "customer":{
                      "type":"object",
                      "x-triobase-sensitivity":"INTERNAL",
                      "properties":{"phone":{"type":"string","maxLength":20,"x-triobase-sensitivity":"SENSITIVE"}}
                    },
                    "items":{
                      "type":"array",
                      "items":{"type":"object","properties":{"sku":{"type":"string"}}}
                    }
                  }
                }
                """);

        var fields = inspector.inspect(schema);

        assertThat(fields).extracting(StructureSchemaInspector.NormalizedField::jsonPointer)
                .containsExactly("/customer", "/customer/phone", "/items", "/items/*/sku");
        assertThat(fields.get(0).required()).isTrue();
        assertThat(fields.get(1).sensitivity()).isEqualTo(SensitivityLevel.SENSITIVE);
        assertThat(fields.get(1).constraints().path("maxLength").asInt()).isEqualTo(20);
    }

    @Test
    void rejectsUnknownRequiredField() throws Exception {
        var schema = OBJECT_MAPPER.readTree("""
                {"type":"object","required":["missing"],"properties":{}}
                """);

        assertThatThrownBy(() -> inspector.inspect(schema))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("REQUIRED_FIELD_UNKNOWN");
    }

    @Test
    void rejectsUnsupportedType() throws Exception {
        var schema = OBJECT_MAPPER.readTree("""
                {"type":"object","properties":{"payload":{"type":"function"}}}
                """);

        assertThatThrownBy(() -> inspector.inspect(schema))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("FIELD_TYPE_UNSUPPORTED");
    }
}
