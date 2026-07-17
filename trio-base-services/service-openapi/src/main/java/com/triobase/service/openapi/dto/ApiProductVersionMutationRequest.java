package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.ProductChangeClassification;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
public record ApiProductVersionMutationRequest(@NotBlank String semanticVersion, String documentation, String terms,
        JsonNode scopes, JsonNode trafficPolicy, JsonNode securityPolicy,
        @NotNull ProductChangeClassification changeClassification, String migrationNotice,
        @Valid List<ProductRouteMemberRequest> routes) { }
