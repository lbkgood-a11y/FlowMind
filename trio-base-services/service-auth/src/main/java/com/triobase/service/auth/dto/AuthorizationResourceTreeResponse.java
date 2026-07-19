package com.triobase.service.auth.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AuthorizationResourceTreeResponse {
    private String tenantId;
    private List<Group> groups = new ArrayList<>();

    @Data
    public static class Group {
        private String resourceType;
        private String label;
        private List<ResourceNode> resources = new ArrayList<>();
    }

    @Data
    public static class ResourceNode {
        private String id;
        private String resourceCode;
        private String resourceType;
        private String ownerService;
        private String businessObjectId;
        private String displayName;
        private String lifecycleStatus;
        private LocalDateTime lastSyncedAt;
        private List<ActionNode> actions = new ArrayList<>();
        private List<FieldNode> fields = new ArrayList<>();
        private List<GuardNode> guards = new ArrayList<>();
    }

    @Data
    public static class ActionNode {
        private String actionCode;
        private String actionCategory;
        private String description;
        private List<String> guardCodes = new ArrayList<>();
        private Short status;
    }

    @Data
    public static class FieldNode {
        private String fieldKey;
        private String fieldLabel;
        private String fieldType;
        private String sensitivityClassification;
        private String defaultMaskStrategy;
        private Short status;
    }

    @Data
    public static class GuardNode {
        private String guardCode;
        private String ownerService;
        private String description;
        private Short status;
    }
}
