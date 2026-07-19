package com.triobase.service.action.exception;

import com.triobase.common.action.enums.ActionErrorCategory;
import lombok.Getter;

@Getter
public class ActionRuntimeException extends RuntimeException {

    private final int code;
    private final ActionErrorCategory category;
    private final String field;

    public ActionRuntimeException(int code,
                                  ActionErrorCategory category,
                                  String message) {
        this(code, category, message, null, null);
    }

    public ActionRuntimeException(int code,
                                  ActionErrorCategory category,
                                  String message,
                                  String field,
                                  Throwable cause) {
        super(message, cause);
        this.code = code;
        this.category = category;
        this.field = field;
    }
}
