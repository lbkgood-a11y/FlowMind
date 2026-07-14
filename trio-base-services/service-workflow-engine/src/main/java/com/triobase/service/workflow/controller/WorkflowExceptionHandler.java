package com.triobase.service.workflow.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.workflow.dto.FormValidationErrorResponse;
import com.triobase.service.workflow.dto.ProcessVersionConflictResponse;
import com.triobase.service.workflow.exception.FormDataValidationException;
import com.triobase.service.workflow.exception.ProcessVersionConflictException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.triobase.service.workflow.controller")
public class WorkflowExceptionHandler {

    @ExceptionHandler(FormDataValidationException.class)
    public R<FormValidationErrorResponse> handleFormDataValidation(FormDataValidationException exception) {
        return R.fail(40000, exception.getMessage(),
                new FormValidationErrorResponse(exception.getFieldErrors()));
    }

    @ExceptionHandler(ProcessVersionConflictException.class)
    public R<ProcessVersionConflictResponse> handleVersionConflict(ProcessVersionConflictException exception) {
        return R.fail(40900, exception.getMessage(), exception.getConflict());
    }
}
