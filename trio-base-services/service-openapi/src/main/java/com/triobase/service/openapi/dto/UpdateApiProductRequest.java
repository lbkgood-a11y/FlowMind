package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.ProductVisibility;
import com.triobase.service.openapi.domain.enums.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record UpdateApiProductRequest(@NotBlank String displayName, @NotBlank String ownerId, String audience,
        @NotNull RiskLevel riskLevel, ProductVisibility visibility, String documentation, String terms,
        JsonNode defaultScopes, JsonNode defaultTrafficPolicy, JsonNode defaultSecurityPolicy) { }
