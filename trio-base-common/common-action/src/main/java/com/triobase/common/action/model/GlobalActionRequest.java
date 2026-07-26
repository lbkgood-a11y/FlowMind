package com.triobase.common.action.model;

import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class GlobalActionRequest {
    private String actionId;
    private String actionType;
    private ActionSource source;
    private ActionActor actor = new ActionActor();
    private ActionTarget target = new ActionTarget();
    private Map<String, Object> payload = new LinkedHashMap<>();
    private ActionContext context = new ActionContext();
    private String idempotencyKey;
    private ActionExecutionMode executionMode;

    // -----------------------------------------------------------------------
    // Payload extraction helpers
    // -----------------------------------------------------------------------

    public String string(String key) {
        Object value = payload.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    public Integer integer(String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && !String.valueOf(value).isBlank()) {
            return Integer.parseInt(String.valueOf(value));
        }
        return null;
    }

    public Long longValue(String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null && !String.valueOf(value).isBlank()) {
            return Long.parseLong(String.valueOf(value));
        }
        return null;
    }
}
