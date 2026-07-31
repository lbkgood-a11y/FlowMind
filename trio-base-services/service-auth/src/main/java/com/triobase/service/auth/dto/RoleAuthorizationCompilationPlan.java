package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RoleAuthorizationCompilationPlan {
    private String tenantId;
    private String roleId;
    private String draftId;
    private String catalogId;
    private Long catalogVersion;
    private Long intentVersion;
    private String businessSummary;
    private List<GrantProjection> grants = new ArrayList<>();
    private List<DataProjection> dataPolicies = new ArrayList<>();
    private List<FieldProjection> fieldPolicies = new ArrayList<>();
    private List<GuardProjection> guards = new ArrayList<>();

    @Data
    public static class GrantProjection {
        private String capabilityCode;
        private String resourceCode;
        private String actionCode;
        private String effect = "ALLOW";
    }

    @Data
    public static class DataProjection {
        private String capabilityCode;
        private String resourceCode;
        private String actionCode;
        private String scopeType;
        private List<String> organizationIds = new ArrayList<>();
    }

    @Data
    public static class FieldProjection {
        private String capabilityCode;
        private String resourceCode;
        private String fieldKey;
        private String readMode;
        private String writeMode;
        private String maskStrategy;
    }

    @Data
    public static class GuardProjection {
        private String capabilityCode;
        private String resourceCode;
        private String actionCode;
        private String guardCode;
        private String constraintJson;
    }

    @Data
    public static class FieldRuleIntent {
        private String fieldKey;
        private String readMode;
        private String writeMode;
        private String maskStrategy;
    }
}
