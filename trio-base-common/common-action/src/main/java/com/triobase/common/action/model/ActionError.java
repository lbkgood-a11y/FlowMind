package com.triobase.common.action.model;

import com.triobase.common.action.enums.ActionErrorCategory;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ActionError {
    private String code;
    private ActionErrorCategory category;
    private String message;
    private String field;
    private Map<String, Object> details = new LinkedHashMap<>();

    public static ActionError of(String code, ActionErrorCategory category, String message) {
        ActionError error = new ActionError();
        error.setCode(code);
        error.setCategory(category);
        error.setMessage(message);
        return error;
    }
}
