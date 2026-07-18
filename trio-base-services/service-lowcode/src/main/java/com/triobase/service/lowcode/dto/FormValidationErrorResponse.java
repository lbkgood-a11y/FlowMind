package com.triobase.service.lowcode.dto;

import java.util.List;

public record FormValidationErrorResponse(List<FormFieldValidationError> fieldErrors) {
}
