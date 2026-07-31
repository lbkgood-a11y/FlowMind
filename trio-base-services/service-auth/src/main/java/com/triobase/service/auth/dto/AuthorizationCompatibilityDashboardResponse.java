package com.triobase.service.auth.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AuthorizationCompatibilityDashboardResponse {
    String tenantId;
    long catalogCapabilityCount;
    long catalogReadyCount;
    long catalogNotReadyCount;
    long totalRoleCount;
    long publishedRoleCount;
    long pendingMigrationRoleCount;
    long decisionEquivalentRoleCount;
    long decisionMismatchRoleCount;
    long missingProjectionCount;
    long unintendedExpansionCount;
    long unresolvedExpansionReviewCount;
    long openDriftCount;
    long publicationFailureCount;
    long rollbackCount;
    int compatibilityQueryCount;
    int roleDetailLimit;
    boolean roleDetailsTruncated;
    long lowcodeUnresolvedOwnershipCount;
    boolean lowcodeOwnershipDiagnosticsAvailable;
    boolean cutoverReady;
    List<String> blockers;
    List<RoleStatus> roleStatuses;

    @Value
    @Builder
    public static class RoleStatus {
        String roleId;
        String roleName;
        String status;
        long missingProjectionCount;
        long unintendedExpansionCount;
    }
}
