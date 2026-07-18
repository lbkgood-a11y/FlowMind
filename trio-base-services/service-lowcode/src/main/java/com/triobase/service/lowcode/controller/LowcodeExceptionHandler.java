package com.triobase.service.lowcode.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.lowcode.dto.FormValidationErrorResponse;
import com.triobase.service.lowcode.exception.FormDataValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.triobase.service.lowcode.controller")
public class LowcodeExceptionHandler {

    @ExceptionHandler(FormDataValidationException.class)
    public R<FormValidationErrorResponse> handleFormDataValidation(FormDataValidationException exception) {
        return R.fail(40013, exception.getMessage(),
                new FormValidationErrorResponse(exception.getFieldErrors()));
    }
}
