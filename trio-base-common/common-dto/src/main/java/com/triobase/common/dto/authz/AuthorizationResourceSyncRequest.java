package com.triobase.common.dto.authz;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuthorizationResourceSyncRequest {
    private String tenantId;
    private String ownerService;
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private Integer aggregateVersion;
    private String operation;
    private String snapshotHash;
    private List<Resource> resources = new ArrayList<>();

    @Data
    public static class Resource {
        private String resourceCode;
        private String resourceType;
        private String displayName;
        private String businessObjectId;
        private String lifecycleStatus;
        private Boolean globalResource;
        private Boolean readHideEnforced;
        private Boolean readMaskEnforced;
        private Boolean writeDenyEnforced;
        private String metadataJson;
        private List<Action> actions = new ArrayList<>();
        private List<Field> fields = new ArrayList<>();
        private List<Guard> guards = new ArrayList<>();
    }

    @Data
    public static class Action {
        private String actionCode;
        private String actionCategory;
        private String description;
        private Boolean dataScopeSupported;
        private Boolean dataScopeEnforced;
        private Integer status;
        private List<String> guardCodes = new ArrayList<>();
    }

    @Data
    public static class Field {
        private String fieldKey;
        private String fieldLabel;
        private String fieldType;
        private String sensitivityClassification;
        private String defaultMaskStrategy;
        private Integer status;
    }

    @Data
    public static class Guard {
        private String guardCode;
        private String ownerService;
        private String supportedResourceTypes;
        private String configSchemaJson;
        private String description;
        private Integer status;
    }
}
