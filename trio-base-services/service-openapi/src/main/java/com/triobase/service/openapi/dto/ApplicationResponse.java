package com.triobase.service.openapi.dto;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.RiskLevel;
public record ApplicationResponse(String applicationId,String tenantId,String applicationKey,String displayName,
        String ownerId,String purpose,RiskLevel riskLevel,ApplicationLifecycleState lifecycleState,String suspensionReason) { }
