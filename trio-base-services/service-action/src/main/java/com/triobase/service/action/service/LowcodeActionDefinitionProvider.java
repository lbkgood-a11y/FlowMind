package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.definition.ActionGuardRequirement;
import com.triobase.common.action.enums.ActionAuditLevel;
import com.triobase.common.action.enums.ActionExecutionMode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LowcodeActionDefinitionProvider implements ActionDefinitionProvider {

    private static final String OWNER_SERVICE = "service-lowcode";
    private static final String TARGET_TYPE = "LOWCODE_FORM";

    @Override
    public List<ActionDefinition> definitions() {
        return List.of(
                formAction("lowcode.form.create", "CREATE"),
                formAction("lowcode.form.save", "SAVE"),
                formAction("lowcode.form.submit", "SUBMIT"),
                workflowRetryAction());
    }

    private ActionDefinition formAction(String actionType, String permission) {
        ActionDefinition definition = new ActionDefinition();
        definition.setActionType(actionType);
        definition.setOwnerService(OWNER_SERVICE);
        definition.setTargetType(TARGET_TYPE);
        definition.setDisplayName(actionType);
        definition.setRequiredPermission(permission);
        definition.setExecutionMode(ActionExecutionMode.SYNC);
        definition.setAuditLevel(ActionAuditLevel.NORMAL);
        definition.setPrimary("lowcode.form.submit".equals(actionType));
        definition.setTargetStatus(status(permission));
        definition.setTargetStatusGroup("LOWCODE_FORM");
        definition.getDefaultRefreshScopes().addAll(List.of("document", "list", "actions", "timeline"));
        definition.getRequiredGuards().add(guard("LOWCODE_FORM_ACTIONABLE", "低代码表单当前可执行"));
        definition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["appKey", "actionCode", "data"],
                  "additionalProperties": false,
                  "properties": {
                    "appKey": {"type": "string"},
                    "version": {"type": "integer"},
                    "actionCode": {"type": "string"},
                    "title": {"type": "string"},
                    "data": {"type": "object"}
                  }
                }
                """);
        return definition;
    }

    private ActionDefinition workflowRetryAction() {
        ActionDefinition definition = new ActionDefinition();
        definition.setActionType("lowcode.workflow.retry");
        definition.setOwnerService(OWNER_SERVICE);
        definition.setTargetType(TARGET_TYPE);
        definition.setDisplayName("lowcode.workflow.retry");
        definition.setRequiredPermission("EDIT");
        definition.setExecutionMode(ActionExecutionMode.SYNC);
        definition.setAuditLevel(ActionAuditLevel.NORMAL);
        definition.setTargetStatus("RETRYING");
        definition.setTargetStatusGroup("LOWCODE_WORKFLOW");
        definition.getDefaultRefreshScopes().addAll(List.of("document", "actions", "timeline", "workflow"));
        definition.getRequiredGuards().add(guard("LOWCODE_WORKFLOW_RETRYABLE", "低代码工作流当前可重试"));
        definition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["appKey", "instanceId"],
                  "additionalProperties": false,
                  "properties": {
                    "appKey": {"type": "string"},
                    "version": {"type": "integer"},
                    "instanceId": {"type": "string"},
                    "actionCode": {"type": "string"},
                    "idempotencyKey": {"type": "string"}
                  }
                }
                """);
        return definition;
    }

    private ActionGuardRequirement guard(String code, String description) {
        ActionGuardRequirement guard = new ActionGuardRequirement();
        guard.setGuardCode(code);
        guard.setOwnerService(OWNER_SERVICE);
        guard.setDescription(description);
        return guard;
    }

    private String status(String permission) {
        return switch (permission) {
            case "CREATE" -> "CREATED";
            case "SAVE" -> "DRAFT";
            case "SUBMIT" -> "SUBMITTED";
            default -> "UPDATED";
        };
    }
}
