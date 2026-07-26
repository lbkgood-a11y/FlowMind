package com.triobase.common.openapi.mapping;

import com.fasterxml.jackson.databind.JsonNode;
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
