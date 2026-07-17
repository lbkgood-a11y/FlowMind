package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
public record SubscriptionRouteOverrideRequest(@NotBlank String routeKey, boolean excluded, JsonNode allowedOperations,
        JsonNode requiredScopes, JsonNode quotaOverride, JsonNode sourceNetworks,
        JsonNode structureVersionIds, JsonNode fieldRestrictions) { }
