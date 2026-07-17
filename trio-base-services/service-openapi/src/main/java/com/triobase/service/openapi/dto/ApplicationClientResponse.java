package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.Environment;
import java.time.LocalDateTime;
public record ApplicationClientResponse(String clientId,String applicationId,String tenantId,Environment environment,
        String clientKey,ApplicationLifecycleState lifecycleState,JsonNode networkPolicy,JsonNode securityPolicy,
        LocalDateTime expiresAt,String suspensionReason) { }
