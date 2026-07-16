package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveMappingContractTestRequest(
        @NotBlank String testName,
        @NotNull JsonNode inputPayload,
        JsonNode expectedOutput,
        String expectedErrorCode,
        boolean required,
        boolean enabled) {
}
