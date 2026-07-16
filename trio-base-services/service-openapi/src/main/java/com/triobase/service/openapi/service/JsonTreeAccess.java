package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.exception.BizException;
import org.springframework.stereotype.Component;

@Component
public class JsonTreeAccess {

    public JsonNode read(JsonNode root, String pointer) {
        if (root == null || pointer == null || pointer.isBlank()) {
            return null;
        }
        JsonNode value = root.at(pointer);
        return value.isMissingNode() ? null : value;
    }

    public void write(ObjectNode root, String pointer, JsonNode value) {
        if (pointer == null || !pointer.startsWith("/") || pointer.contains("/*")) {
            throw new BizException(40023, "OPENAPI_MAPPING_TARGET_POINTER_UNSUPPORTED:" + pointer);
        }
        String[] segments = pointer.substring(1).split("/");
        ObjectNode current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            String segment = unescape(segments[index]);
            JsonNode existing = current.get(segment);
            if (existing == null || existing.isNull()) {
                current = current.putObject(segment);
            } else if (existing.isObject()) {
                current = (ObjectNode) existing;
            } else {
                throw new BizException(40023, "OPENAPI_MAPPING_TARGET_PATH_CONFLICT:" + pointer);
            }
        }
        current.set(unescape(segments[segments.length - 1]), value == null ? root.nullNode() : value.deepCopy());
    }

    private String unescape(String value) {
        return value.replace("~1", "/").replace("~0", "~");
    }
}
