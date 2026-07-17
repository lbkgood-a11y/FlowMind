package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CaptureExecutionDiagnosticRequest(
        JsonNode requestPayload,
        JsonNode responsePayload,
        JsonNode redactionPolicy) {
}
