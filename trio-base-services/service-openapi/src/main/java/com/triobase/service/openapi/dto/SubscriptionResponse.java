package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.SubscriptionLifecycleState;
import java.time.LocalDateTime;
public record SubscriptionResponse(String subscriptionId,String tenantId,String applicationClientId,String apiProductVersionId,
        Environment environment,SubscriptionLifecycleState lifecycleState,JsonNode effectiveScopes,JsonNode overrides,
        LocalDateTime effectiveFrom,LocalDateTime effectiveUntil,String suspensionReason) { }
