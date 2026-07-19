package com.triobase.service.action.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.util.ActionPayloadRedactor;
import com.triobase.service.action.exception.ActionRuntimeException;

public final class ActionJsonSupport {

    private static final int ERROR_CODE = 50041;

    private ActionJsonSupport() {
    }

    public static String boundedJson(ObjectMapper objectMapper, Object value) {
        return boundedJson(objectMapper, value, 4_000);
    }

    public static String boundedJson(ObjectMapper objectMapper, Object value, int maxLength) {
        if (value == null) {
            return null;
        }
        try {
            return ActionPayloadRedactor.boundedSummary(
                    objectMapper.writeValueAsString(value),
                    maxLength);
        } catch (JsonProcessingException ex) {
            throw new ActionRuntimeException(
                    ERROR_CODE,
                    ActionErrorCategory.SYSTEM,
                    "ACTION_JSON_SERIALIZATION_FAILED",
                    null,
                    ex);
        }
    }
}
