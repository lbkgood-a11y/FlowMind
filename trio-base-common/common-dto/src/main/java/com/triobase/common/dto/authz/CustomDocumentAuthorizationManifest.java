package com.triobase.common.dto.authz;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CustomDocumentAuthorizationManifest {
    private String tenantId;
    private String serviceName;
    private List<Document> documents = new ArrayList<>();

    @Data
    public static class Document {
        private String code;
        private String documentType;
        private String displayName;
        private String businessObjectId;
        private String lifecycleStatus = "ACTIVE";
        private String metadataJson;
        private List<Action> actions = new ArrayList<>();
        private List<Field> fields = new ArrayList<>();
        private List<Guard> guards = new ArrayList<>();
    }

    @Data
    public static class Action {
        private String actionCode;
        private String description;
        private List<String> guardCodes = new ArrayList<>();
    }

    @Data
    public static class Field {
        private String fieldKey;
        private String fieldLabel;
        private String fieldType;
        private String sensitivityClassification;
        private String defaultMaskStrategy;
    }

    @Data
    public static class Guard {
        private String guardCode;
        private String ownerService;
        private String supportedResourceTypes;
        private String configSchemaJson;
        private String description;
    }
}
