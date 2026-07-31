package com.triobase.service.auth.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class LegacyRoleAuthorizationAnalysisResponse {
    String tenantId;
    String roleId;
    long exactCount;
    long partialCount;
    long ambiguousCount;
    long unmappedCount;
    boolean reviewRequired;
    boolean permissionExpansionDetected;
    String draftId;
    List<Entry> entries;

    @Value
    @Builder
    public static class Entry {
        String resourceCode;
        String actionCode;
        String result;
        List<String> capabilityIds;
        List<String> capabilityNames;
        String explanation;
    }
}
