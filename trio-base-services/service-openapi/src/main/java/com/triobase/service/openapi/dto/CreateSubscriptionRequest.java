package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
public record CreateSubscriptionRequest(@NotBlank String applicationClientId,@NotBlank String apiProductVersionId,
        JsonNode requestedScopes,@Valid List<SubscriptionRouteOverrideRequest> routeOverrides,
        LocalDateTime effectiveFrom,LocalDateTime effectiveUntil) { }
