package com.triobase.service.action.controller;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.R;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.action.dto.ActionErrorResponse;
import com.triobase.service.action.exception.ActionRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.triobase.service.action")
public class ActionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ActionExceptionHandler.class);

    @ExceptionHandler(ActionRuntimeException.class)
    public R<ActionErrorResponse> handleActionRuntime(ActionRuntimeException exception) {
        ActionError error = ActionError.of(String.valueOf(exception.getCode()),
                exception.getCategory(),
                exception.getMessage());
        error.setField(exception.getField());
        return R.fail(exception.getCode(), exception.getMessage(),
                ActionErrorResponse.rejected(error, TraceUtil.getTraceId()));
    }

    @ExceptionHandler(BizException.class)
    public R<ActionErrorResponse> handleBizException(BizException exception) {
        log.warn("Action BizException code={} message={}", exception.getCode(), exception.getMessage());
        ActionError error = ActionError.of(String.valueOf(exception.getCode()),
                ActionErrorCategory.EXECUTION,
                exception.getMessage());
        return R.fail(exception.getCode(), exception.getMessage(),
                ActionErrorResponse.rejected(error, TraceUtil.getTraceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<ActionErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String field = exception.getBindingResult().getFieldErrors().isEmpty()
                ? null
                : exception.getBindingResult().getFieldErrors().get(0).getField();
        String message = exception.getBindingResult().getFieldErrors().isEmpty()
                ? "ACTION_REQUEST_INVALID"
                : exception.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        ActionError error = ActionError.of("40041", ActionErrorCategory.VALIDATION, message);
        error.setField(field);
        return R.fail(40041, message, ActionErrorResponse.rejected(error, TraceUtil.getTraceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<ActionErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        ActionError error = ActionError.of("40042",
                ActionErrorCategory.VALIDATION,
                exception.getMessage());
        return R.fail(40042, exception.getMessage(),
                ActionErrorResponse.rejected(error, TraceUtil.getTraceId()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public R<ActionErrorResponse> handleDuplicate(DuplicateKeyException exception) {
        ActionError error = ActionError.of("40941",
                ActionErrorCategory.IDEMPOTENCY,
                "ACTION_DUPLICATE_OR_CONFLICT");
        return R.fail(40941, "ACTION_DUPLICATE_OR_CONFLICT",
                ActionErrorResponse.rejected(error, TraceUtil.getTraceId()));
    }
}
