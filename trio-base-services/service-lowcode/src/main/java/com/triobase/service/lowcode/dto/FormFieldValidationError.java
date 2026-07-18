package com.triobase.service.lowcode.dto;

public record FormFieldValidationError(
        String field,
        String code,
        String message,
        String keyword) {
}
