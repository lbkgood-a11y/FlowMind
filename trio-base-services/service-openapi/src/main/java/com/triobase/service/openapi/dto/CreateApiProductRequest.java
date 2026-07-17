package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.ProductChangeClassification;
import com.triobase.service.openapi.domain.enums.ProductVisibility;
import com.triobase.service.openapi.domain.enums.RiskLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
public record CreateApiProductRequest(String tenantId, @NotBlank String productKey, @NotBlank String displayName,
        @NotBlank String ownerId, String audience, @NotNull RiskLevel riskLevel, ProductVisibility visibility,
        String documentation, String terms, JsonNode defaultScopes, JsonNode defaultTrafficPolicy,
        JsonNode defaultSecurityPolicy, @NotBlank String semanticVersion,
        @NotNull ProductChangeClassification changeClassification, String migrationNotice,
        @Valid List<ProductRouteMemberRequest> routes) { }
