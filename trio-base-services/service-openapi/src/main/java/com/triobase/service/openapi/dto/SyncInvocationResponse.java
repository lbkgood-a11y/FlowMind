package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
public record SyncInvocationResponse(String executionId,int partnerStatus,JsonNode body,String traceId,long durationMillis) { }
