package com.triobase.service.workflow.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 流程包完整定义 DTO（对应 process_json 字段的反序列化结构）
 */
@Data
public class ProcessPackageDefinition {
    private String version;
    private String processKey;
    private String name;
    private String category;
    private FormSchema form;
    private FlowSchema flow;
    private PermissionSchema permissions;
    @JsonAlias("businessBinding")
    private BusinessObjectBinding businessObject;
    private LaunchPolicy launchPolicy;
    @JsonAlias("businessPermissions")
    private BusinessPermissionPolicy permissionPolicy;
    private ClosurePolicy closurePolicy;
    private AgentFollowUpPolicy agentFollowUpPolicy;
    @JsonProperty("extends")
    private ExtendsSchema extendsConfig;

    @Data
    public static class FormSchema {
        private Map<String, Object> schema;
        private Map<String, Object> uiSchema;
    }

    @Data
    public static class FlowSchema {
        private List<NodeSchema> nodes;
    }

    @Data
    public static class NodeSchema {
        private String id;
        private String type;       // START / APPROVAL / COUNTERSIGN / CONDITION / NOTIFY / SERVICE_TASK / END
        private String name;
        private Assignment assignment;
        private String strategy;    // COUNTERSIGN: ALL / ANY
        private List<NextCondition> next;
    }

    @Data
    public static class Assignment {
        private String type;       // ROLE / DEPT / USER / DYNAMIC / MANAGER / SYSTEM
        private String roleCode;
        private String deptCode;
        private String dimensionCode;
        private String userId;
        private String fieldKey;   // DYNAMIC 模式下从表单字段取值
    }

    @Data
    public static class NextCondition {
        private String condition;  // 表达式，如 "amount > 5000"，"true" 为默认分支
        private String target;     // 目标节点 ID
    }

    @Data
    public static class PermissionSchema {
        private List<String> start;
        private List<String> view;
        private List<String> admin;
        private List<DataRule> data;
    }

    @Data
    public static class DataRule {
        private String resource;
        private String rule;
    }

    @Data
    public static class BusinessObjectBinding {
        @JsonAlias({"businessType", "objectType"})
        private String typeCode;
        private BusinessRefSource businessRef;
    }

    @Data
    public static class BusinessRefSource {
        private String sourceType; // FORM_FIELD / PAGE_CONTEXT / API_INPUT / PROCESS_CONTEXT / FIXED
        private String fieldKey;
        private String contextKey;
        private String fixedValue;
    }

    @Data
    public static class LaunchPolicy {
        @JsonAlias({"businessType", "objectType"})
        private String businessObjectType;
        private List<String> modes;
        private List<String> allowedStatuses;
        private String submitActionCode;
        private String createActionCode;
        private BusinessRefSource businessRef;
        private List<ClosureEffectDefinition> startEffects;
    }

    @Data
    public static class BusinessPermissionPolicy {
        private String submitActionCode;
        private String viewActionCode;
        private String approveActionCode;
        private String retryClosureActionCode;
        private String agentFollowUpActionCode;
        private Map<String, String> taskActionCodes;
    }

    @Data
    public static class ClosurePolicy {
        @JsonAlias({"businessType", "objectType"})
        private String businessObjectType;
        private BusinessRefSource businessRef;
        private Map<String, List<ClosureEffectDefinition>> outcomes;
        private List<ClosureEffectDefinition> failureEffects;
    }

    @Data
    public static class AgentFollowUpPolicy {
        private List<ClosureEffectDefinition> actions;
    }

    @Data
    public static class ClosureEffectDefinition {
        private String effectKey;
        private String actionCode;
        private String agentActionCode;
        private String eventCode;
        private String mode;
        private Map<String, Object> params;
        private String executorKey;
        private String url;
        private String sql;
        private String script;
        @JsonAlias({"class", "className", "dynamicClass", "executorClass"})
        private String className;
        @JsonAlias({"prompt", "freePrompt"})
        private String prompt;
        private Map<String, Object> connector;
        private Map<String, Object> toolCall;
        private Map<String, Object> execution;
    }

    @Data
    public static class ExtendsSchema {
        private List<PrintTemplate> printTemplates;
        private AttachmentRule attachments;
        private List<NotificationRule> notifications;
    }

    @Data
    public static class PrintTemplate {
        private String id;
        private String template;
    }

    @Data
    public static class AttachmentRule {
        private Integer maxCount;
        private String maxSize;
    }

    @Data
    public static class NotificationRule {
        private String on;
        private String to;
        private String channel;
    }
}
