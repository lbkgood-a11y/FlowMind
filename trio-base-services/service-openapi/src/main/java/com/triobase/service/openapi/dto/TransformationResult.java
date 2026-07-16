package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record TransformationResult(
        JsonNode output,
        List<RuleTrace> traces,
        List<String> warnings) {

    public record RuleTrace(int order, String operation, String source, String target, String outcome) {
    }
}
