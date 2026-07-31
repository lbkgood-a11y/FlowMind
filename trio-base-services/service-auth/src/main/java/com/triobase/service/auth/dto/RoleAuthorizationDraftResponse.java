package com.triobase.service.auth.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class RoleAuthorizationDraftResponse {
    String draftId;
    String roleId;
    String status;
    Long version;
    String catalogId;
    String basedReleaseId;
    LocalDateTime validatedAt;
    LocalDateTime validationExpiresAt;
    List<Selection> selections;

    @Value
    @Builder
    public static class Selection {
        String capabilityId;
        String capabilityName;
        String category;
        String selectionSource;
        String effectiveScopeSummary;
        String defaultScopeType;
        List<String> defaultScopeIds;
        String operationScopeType;
        List<String> operationScopeIds;
        String fieldIntentJson;
        String constraintIntentJson;
    }
}
