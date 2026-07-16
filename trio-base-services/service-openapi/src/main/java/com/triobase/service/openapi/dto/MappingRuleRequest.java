package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MappingRuleRequest(
        int order,
        @NotNull MappingOperation operation,
        String sourcePointer,
        @NotBlank String targetPointer,
        JsonNode config,
        boolean required) {
}
