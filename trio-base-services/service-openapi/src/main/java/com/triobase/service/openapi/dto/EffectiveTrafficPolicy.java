package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;
import java.util.List;
public record EffectiveTrafficPolicy(String tenantId,Environment environment,String clientId,String productId,
        String routeKey,String operation,JsonNode policy,List<String> appliedPolicyVersionIds) { }
