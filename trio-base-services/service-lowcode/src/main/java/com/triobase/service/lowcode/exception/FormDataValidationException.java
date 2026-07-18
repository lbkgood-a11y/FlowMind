package com.triobase.service.lowcode.exception;

import com.triobase.service.lowcode.dto.FormFieldValidationError;
import lombok.Getter;

import java.util.List;

@Getter
public class FormDataValidationException extends RuntimeException {

    private final List<FormFieldValidationError> fieldErrors;

    public FormDataValidationException(List<FormFieldValidationError> fieldErrors) {
        super("FORM_DATA_VALIDATION_FAILED");
        this.fieldErrors = List.copyOf(fieldErrors);
    }
}
