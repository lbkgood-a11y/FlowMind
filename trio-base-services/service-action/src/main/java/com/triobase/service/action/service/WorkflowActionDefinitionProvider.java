package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.definition.ActionGuardRequirement;
import com.triobase.common.action.enums.ActionAuditLevel;
import com.triobase.common.action.enums.ActionExecutionMode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorkflowActionDefinitionProvider implements ActionDefinitionProvider {

    private static final String OWNER_SERVICE = "service-workflow-engine";

    @Override
    public List<ActionDefinition> definitions() {
        return List.of(
                processStart(),
                taskAction("process.task.approve", "approve"),
                taskAction("process.task.reject", "reject"),
                taskAction("process.task.transfer", "transfer"),
                taskAction("process.task.addSign", "addSign"),
                closureAction("process.closure.effect.retry", "retryClosure"),
                closureAction("process.closure.effect.markHandled", "retryClosure"));
    }

    private ActionDefinition processStart() {
        ActionDefinition definition = base("process.instance.start", "PROCESS_INSTANCE",
                "/api/v1/process-instances/start:POST", ActionExecutionMode.WORKFLOW);
        definition.setPrimary(true);
        definition.setTargetStatus("RUNNING");
        definition.setTargetStatusGroup("IN_PROGRESS");
        definition.getDefaultRefreshScopes().addAll(List.of("document", "list", "actions", "timeline", "workflow"));
        definition.getRequiredGuards().add(guard("PROCESS_INSTANCE_STARTABLE", "流程实例可启动"));
        definition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["processKey"],
                  "additionalProperties": false,
                  "properties": {
                    "processPackageId": {"type": "string"},
                    "version": {"type": "integer"},
                    "processKey": {"type": "string"},
                    "title": {"type": "string"},
                    "formData": {"type": "object"},
                    "launchMode": {"type": "string"},
                    "businessType": {"type": "string"},
                    "businessId": {"type": "string"},
                    "idempotencyKey": {"type": "string"}
                  }
                }
                """);
        return definition;
    }

    private ActionDefinition taskAction(String actionType, String permission) {
        ActionDefinition definition = base(actionType, "PROCESS_TASK", permission, ActionExecutionMode.SIGNAL);
        definition.setPrimary("process.task.approve".equals(actionType));
        definition.setDanger("process.task.reject".equals(actionType));
        definition.setTargetStatus(targetStatus(actionType));
        definition.setTargetStatusGroup("process.task.reject".equals(actionType) ? "TERMINAL" : "IN_PROGRESS");
        definition.getDefaultRefreshScopes().addAll(List.of("document", "list", "actions", "timeline", "workflow"));
        definition.getRequiredGuards().add(guard("PROCESS_TASK_ACTIONABLE", "流程任务当前可执行"));
        definition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["taskId"],
                  "additionalProperties": false,
                  "properties": {
                    "taskId": {"type": "string"},
                    "operationId": {"type": "string"},
                    "action": {"type": "string"},
                    "comment": {"type": "string"},
                    "targetNodeId": {"type": "string"},
                    "newAssigneeId": {"type": "string"},
                    "newAssigneeName": {"type": "string"},
                    "assigneeId": {"type": "string"},
                    "assigneeName": {"type": "string"}
                  }
                }
                """);
        return definition;
    }

    private ActionDefinition closureAction(String actionType, String permission) {
        ActionDefinition definition = base(actionType, "PROCESS_CLOSURE_EFFECT", permission, ActionExecutionMode.SYNC);
        definition.setTargetStatus("process.closure.effect.markHandled".equals(actionType) ? "HANDLED" : "RETRYING");
        definition.setTargetStatusGroup("PROCESS_CLOSURE_EFFECT");
        definition.getDefaultRefreshScopes().addAll(List.of("document", "list", "actions", "timeline", "workflow"));
        definition.getRequiredGuards().add(guard("PROCESS_CLOSURE_EFFECT_ACTIONABLE", "闭环补偿项当前可处理"));
        definition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["effectId"],
                  "additionalProperties": false,
                  "properties": {
                    "effectId": {"type": "string"},
                    "reason": {"type": "string"}
                  }
                }
                """);
        return definition;
    }

    private ActionDefinition base(String actionType,
                                  String targetType,
                                  String permission,
                                  ActionExecutionMode mode) {
        ActionDefinition definition = new ActionDefinition();
        definition.setActionType(actionType);
        definition.setOwnerService(OWNER_SERVICE);
        definition.setTargetType(targetType);
        definition.setDisplayName(actionType);
        definition.setRequiredPermission(permission);
        definition.setExecutionMode(mode);
        definition.setAuditLevel(ActionAuditLevel.NORMAL);
        return definition;
    }

    private ActionGuardRequirement guard(String code, String description) {
        ActionGuardRequirement guard = new ActionGuardRequirement();
        guard.setGuardCode(code);
        guard.setOwnerService(OWNER_SERVICE);
        guard.setDescription(description);
        return guard;
    }

    private String targetStatus(String actionType) {
        return switch (actionType) {
            case "process.task.approve" -> "APPROVED";
            case "process.task.reject" -> "REJECTED";
            case "process.task.transfer" -> "TRANSFERRED";
            case "process.task.addSign" -> "ADD_SIGN";
            default -> "UPDATED";
        };
    }
}
