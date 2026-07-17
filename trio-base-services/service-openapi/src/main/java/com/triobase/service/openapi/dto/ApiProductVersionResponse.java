package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.ProductChangeClassification;
import com.triobase.service.openapi.domain.enums.ProductVisibility;
import com.triobase.service.openapi.domain.enums.RiskLevel;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import java.util.List;
public record ApiProductVersionResponse(String productId, String productVersionId, String tenantId, String productKey,
        String displayName, String ownerId, RiskLevel riskLevel, ProductVisibility visibility,
        String semanticVersion, VersionLifecycleState lifecycleState, ProductChangeClassification changeClassification,
        String documentation, String terms, JsonNode scopes, JsonNode trafficPolicy, JsonNode securityPolicy,
        String migrationNotice, List<ProductRouteMemberRequest> routes) { }
