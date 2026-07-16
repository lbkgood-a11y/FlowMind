package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantExtensionValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final TenantExtensionValidator validator = new TenantExtensionValidator();

    @Test
    void acceptsOptionalTenantNamespacedField() throws Exception {
        JsonNode parent = schema("""
                {
                  "type":"object",
                  "required":["orderId"],
                  "properties":{
                    "orderId":{"type":"string","x-triobase-semantic-id":"order.id","x-triobase-sensitivity":"INTERNAL"}
                  }
                }
                """);
        JsonNode extension = schema("""
                {
                  "type":"object",
                  "required":["orderId"],
                  "properties":{
                    "orderId":{"type":"string","x-triobase-semantic-id":"order.id","x-triobase-sensitivity":"INTERNAL"},
                    "costCenter":{"type":"string","x-triobase-tenant-id":"tenant-a"}
                  }
                }
                """);

        assertThatCode(() -> validator.validate(parent, extension, "tenant-a"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInheritedTypeChange() throws Exception {
        JsonNode parent = schema("""
                {"type":"object","properties":{"amount":{"type":"number"}}}
                """);
        JsonNode extension = schema("""
                {"type":"object","properties":{"amount":{"type":"string"}}}
                """);

        assertThatThrownBy(() -> validator.validate(parent, extension, "tenant-a"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("INHERITED_METADATA_CHANGED");
    }

    @Test
    void rejectsRequiredTenantField() throws Exception {
        JsonNode parent = schema("""
                {"type":"object","properties":{}}
                """);
        JsonNode extension = schema("""
                {
                  "type":"object",
                  "required":["costCenter"],
                  "properties":{"costCenter":{"type":"string","x-triobase-tenant-id":"tenant-a"}}
                }
                """);

        assertThatThrownBy(() -> validator.validate(parent, extension, "tenant-a"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("FIELD_MUST_BE_OPTIONAL");
    }

    @Test
    void rejectsFieldWithoutTenantMarker() throws Exception {
        JsonNode parent = schema("""
                {"type":"object","properties":{}}
                """);
        JsonNode extension = schema("""
                {"type":"object","properties":{"costCenter":{"type":"string"}}}
                """);

        assertThatThrownBy(() -> validator.validate(parent, extension, "tenant-a"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("FIELD_NAMESPACE_REQUIRED");
    }

    private JsonNode schema(String json) throws Exception {
        return OBJECT_MAPPER.readTree(json);
    }
}
