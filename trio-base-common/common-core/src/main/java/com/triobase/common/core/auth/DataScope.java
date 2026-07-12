package com.triobase.common.core.auth;

import java.util.Collections;
import java.util.List;

/**
 * Standard data-scope DTO shared by business services.
 */
public record DataScope(
        String userId,
        String resourceCode,
        String actionCode,
        boolean restrictive,
        boolean orgContextResolved,
        List<String> roleIds,
        List<Policy> policies
) {
    public DataScope(String resourceCode,
                     String actionCode,
                     boolean restrictive,
                     List<Policy> policies) {
        this(null, resourceCode, actionCode, restrictive, false, List.of(), policies);
    }

    public DataScope {
        roleIds = roleIds != null ? List.copyOf(roleIds) : Collections.emptyList();
        policies = policies != null ? List.copyOf(policies) : Collections.emptyList();
    }

    public static DataScope restrictive(String userId, String resourceCode, String actionCode) {
        return new DataScope(userId, resourceCode, actionCode, true, false, List.of(), List.of());
    }

    public boolean allowsAll() {
        return policies.stream()
                .filter(policy -> "ALLOW".equalsIgnoreCase(policy.effect()))
                .flatMap(policy -> policy.dimensions().stream())
                .anyMatch(dimension -> "ALL".equalsIgnoreCase(dimension.scopeType()));
    }

    public boolean allowsSelf() {
        return policies.stream()
                .filter(policy -> "ALLOW".equalsIgnoreCase(policy.effect()))
                .flatMap(policy -> policy.dimensions().stream())
                .anyMatch(dimension -> "SELF".equalsIgnoreCase(dimension.scopeType()));
    }

    public record Policy(
            String policyId,
            String roleId,
            String effect,
            String combineMode,
            List<Dimension> dimensions
    ) {
        public Policy(String roleId,
                      String effect,
                      String combineMode,
                      List<Dimension> dimensions) {
            this(null, roleId, effect, combineMode, dimensions);
        }

        public Policy {
            dimensions = dimensions != null ? List.copyOf(dimensions) : Collections.emptyList();
        }
    }

    public record Dimension(
            String dimensionCode,
            String scopeType,
            List<String> orgUnitIds
    ) {
        public Dimension {
            orgUnitIds = orgUnitIds != null ? List.copyOf(orgUnitIds) : Collections.emptyList();
        }
    }
}
