package com.triobase.common.dto.form;

import java.util.List;

public record FormValidationErrorResponse(List<FormFieldValidationError> fieldErrors) {
}
