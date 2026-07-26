package com.triobase.common.dto.form;

public record FormFieldValidationError(
        String field,
        String code,
        String message,
        String keyword) {
}
