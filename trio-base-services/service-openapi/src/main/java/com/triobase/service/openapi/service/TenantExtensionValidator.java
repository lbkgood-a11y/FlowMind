package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Component
public class TenantExtensionValidator {

    private static final int INVALID_EXTENSION = 40915;
    private static final String TENANT_MARKER = "x-triobase-tenant-id";
    private static final String SEMANTIC_ID = "x-triobase-semantic-id";
    private static final String SENSITIVITY = "x-triobase-sensitivity";

    public void validate(JsonNode parentSchema, JsonNode extensionSchema, String tenantId) {
        if (parentSchema == null || extensionSchema == null || tenantId == null) {
            fail("OPENAPI_TENANT_EXTENSION_CONTEXT_INVALID");
        }
        compareObject(parentSchema, extensionSchema, tenantId, "");
    }

    private void compareObject(JsonNode parent, JsonNode extension, String tenantId, String path) {
        requireSameText(parent, extension, "type", path);
        JsonNode parentProperties = objectNode(parent, "properties");
        JsonNode extensionProperties = objectNode(extension, "properties");
        Set<String> parentRequired = textSet(parent.path("required"));
        Set<String> extensionRequired = textSet(extension.path("required"));
        if (!extensionRequired.containsAll(parentRequired)) {
            fail("OPENAPI_TENANT_EXTENSION_CANNOT_WEAKEN_REQUIRED_FIELDS:" + path);
        }

        Iterator<Map.Entry<String, JsonNode>> parentFields = parentProperties.fields();
        while (parentFields.hasNext()) {
            Map.Entry<String, JsonNode> field = parentFields.next();
            JsonNode extensionField = extensionProperties.get(field.getKey());
            String fieldPath = path + "/properties/" + field.getKey();
            if (extensionField == null) {
                fail("OPENAPI_TENANT_EXTENSION_CANNOT_REMOVE_FIELD:" + fieldPath);
            }
            compareInheritedField(field.getValue(), extensionField, tenantId, fieldPath);
        }

        Iterator<Map.Entry<String, JsonNode>> extensionFields = extensionProperties.fields();
        while (extensionFields.hasNext()) {
            Map.Entry<String, JsonNode> field = extensionFields.next();
            if (parentProperties.has(field.getKey())) {
                continue;
            }
            String fieldPath = path + "/properties/" + field.getKey();
            if (extensionRequired.contains(field.getKey())) {
                fail("OPENAPI_TENANT_EXTENSION_FIELD_MUST_BE_OPTIONAL:" + fieldPath);
            }
            if (!tenantId.equals(field.getValue().path(TENANT_MARKER).asText(null))) {
                fail("OPENAPI_TENANT_EXTENSION_FIELD_NAMESPACE_REQUIRED:" + fieldPath);
            }
        }
    }

    private void compareInheritedField(JsonNode parent, JsonNode extension, String tenantId, String path) {
        requireSameText(parent, extension, "type", path);
        requireSameText(parent, extension, SEMANTIC_ID, path);
        requireSameText(parent, extension, SENSITIVITY, path);
        if ("object".equals(parent.path("type").asText())) {
            compareObject(parent, extension, tenantId, path);
        }
        if ("array".equals(parent.path("type").asText()) && parent.path("items").isObject()) {
            compareInheritedField(parent.path("items"), extension.path("items"), tenantId, path + "/items");
        }
    }

    private void requireSameText(JsonNode parent, JsonNode extension, String property, String path) {
        String parentValue = parent.path(property).asText(null);
        String extensionValue = extension.path(property).asText(null);
        if (parentValue == null ? extensionValue != null : !parentValue.equals(extensionValue)) {
            fail("OPENAPI_TENANT_EXTENSION_INHERITED_METADATA_CHANGED:" + path + "/" + property);
        }
    }

    private JsonNode objectNode(JsonNode node, String property) {
        JsonNode value = node.path(property);
        if (!value.isMissingNode() && !value.isObject()) {
            fail("OPENAPI_TENANT_EXTENSION_SCHEMA_INVALID:" + property);
        }
        return value;
    }

    private Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        if (array.isMissingNode()) {
            return values;
        }
        if (!array.isArray()) {
            fail("OPENAPI_TENANT_EXTENSION_REQUIRED_INVALID");
        }
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private void fail(String message) {
        throw new BizException(INVALID_EXTENSION, message);
    }
}
