package com.triobase.service.auth.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PageCapabilityDiagnosticResponse {
    String capabilityId;
    String tenantId;
    String catalogId;
    Long catalogVersion;
    String pageCode;
    String capabilityCode;
    String readiness;
    String readinessMessage;
    List<Target> targets;
    List<String> requiredCapabilityCodes;

    @Value
    @Builder
    public static class Target {
        String resourceCode;
        String actionCode;
        String targetKind;
        Boolean required;
        Boolean active;
    }
}
