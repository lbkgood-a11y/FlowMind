package com.triobase.service.workflow.dto;

public record FormFieldValidationError(
        String field,
        String code,
        String message,
        String keyword) {
}
