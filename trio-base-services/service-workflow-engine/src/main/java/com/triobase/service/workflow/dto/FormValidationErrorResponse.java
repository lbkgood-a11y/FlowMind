package com.triobase.service.workflow.dto;

import java.util.List;

public record FormValidationErrorResponse(List<FormFieldValidationError> fieldErrors) {
}
