package com.triobase.service.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BusinessObjectCatalogResponse {
    private BusinessObjectSummaryResponse object;
    private List<StatusItem> statuses = new ArrayList<>();
    private List<FormItem> forms = new ArrayList<>();
    private List<PermissionItem> permissions = new ArrayList<>();
    private List<ActionItem> actions = new ArrayList<>();
    private List<EventItem> events = new ArrayList<>();
    private List<AgentActionItem> agentActions = new ArrayList<>();
    private List<TemplateItem> templates = new ArrayList<>();

    @Data
    public static class StatusItem {
        private String statusCode;
        private String displayName;
        private String statusGroup;
        private Boolean initial;
        private Boolean terminal;
        private Integer sortOrder;
    }

    @Data
    public static class FormItem {
        private String formRole;
        private String displayName;
        private String formDefinitionId;
        private String formKey;
        private Integer formVersion;
        private Boolean required;
        private Integer sortOrder;
    }

    @Data
    public static class PermissionItem {
        private String actionCode;
        private String displayName;
        private String permissionCode;
        private String actionGroup;
        private Integer sortOrder;
    }

    @Data
    public static class ActionItem {
        private String actionCode;
        private String displayName;
        private String actionType;
        @JsonIgnore
        private String executorKey;
        private String modeDefault;
        private String permissionAction;
        private String paramSchemaJson;
        private Integer sortOrder;
    }

    @Data
    public static class EventItem {
        private String eventCode;
        private String displayName;
        private String eventType;
        private String payloadSchemaJson;
        private Integer sortOrder;
    }

    @Data
    public static class AgentActionItem {
        private String agentActionCode;
        private String displayName;
        @JsonIgnore
        private String executorKey;
        private String permissionAction;
        private String paramSchemaJson;
        private String resultSchemaJson;
        private String modeDefault;
        private Integer sortOrder;
    }

    @Data
    public static class TemplateItem {
        private String templateCode;
        private String displayName;
        private String templateType;
        private String configJson;
        private Integer sortOrder;
    }
}
