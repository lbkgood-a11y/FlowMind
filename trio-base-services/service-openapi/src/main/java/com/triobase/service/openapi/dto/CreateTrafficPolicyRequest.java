package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.PolicyScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record CreateTrafficPolicyRequest(String tenantId,@NotNull Environment environment,
        @NotNull PolicyScopeType scopeType,@NotBlank String scopeId,@NotNull JsonNode policyContent) { }
