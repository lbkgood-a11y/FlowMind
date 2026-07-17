package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
public record ApprovalDecisionRequest(boolean approved,JsonNode evidence) { }
