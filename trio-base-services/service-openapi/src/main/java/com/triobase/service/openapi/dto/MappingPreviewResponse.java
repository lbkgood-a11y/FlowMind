package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record MappingPreviewResponse(
        JsonNode output,
        List<TransformationResult.RuleTrace> traces,
        List<String> warnings,
        List<String> sourceValidationErrors,
        List<String> targetValidationErrors) {
}
