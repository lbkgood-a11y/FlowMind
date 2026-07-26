package com.triobase.common.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record StartOrchestrationRequest(JsonNode payload) {
}
