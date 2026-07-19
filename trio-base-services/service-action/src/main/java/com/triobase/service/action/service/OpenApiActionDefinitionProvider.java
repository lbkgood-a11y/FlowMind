package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.definition.ActionGuardRequirement;
import com.triobase.common.action.enums.ActionAuditLevel;
import com.triobase.common.action.enums.ActionExecutionMode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpenApiActionDefinitionProvider implements ActionDefinitionProvider {

    private static final String OWNER_SERVICE = "service-openapi";

    @Override
    public List<ActionDefinition> definitions() {
        return List.of(orchestrationStart(), orchestrationCancel(), stateChangingInvocation(), callbackSignal());
    }

    private ActionDefinition orchestrationStart() {
        ActionDefinition definition = base(
                "integration.orchestration.start",
                "INTEGRATION_ROUTE",
                ActionExecutionMode.WORKFLOW);
        definition.setPrimary(true);
        definition.setTargetStatus("RUNNING");
        definition.setTargetStatusGroup("IN_PROGRESS");
        definition.getDefaultRefreshScopes().addAll(List.of("document", "list", "actions", "timeline", "relatedTables"));
        definition.getRequiredGuards().add(guard("OPENAPI_ORCHESTRATION_STARTABLE", "集成编排当前可启动"));
        definition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["routeKey", "environment", "operation", "idempotencyKey", "admission", "payload"],
                  "additionalProperties": false,
                  "properties": {
                    "routeKey": {"type": "string"},
                    "environment": {"type": "string"},
                    "operation": {"type": "string"},
                    "idempotencyKey": {"type": "string"},
                    "admission": {"type": "object"},
                    "payload": {}
                  }
                }
                """);
        return definition;
    }

    private ActionDefinition orchestrationCancel() {
        ActionDefinition definition = base(
                "integration.orchestration.cancel",
                "INTEGRATION_EXECUTION",
                ActionExecutionMode.SIGNAL);
        definition.setDanger(true);
        definition.setRequiresConfirmation(true);
        definition.setTargetStatus("CANCELLED");
        definition.setTargetStatusGroup("TERMINAL");
        definition.getDefaultRefreshScopes().addAll(List.of("document", "list", "actions", "timeline", "relatedTables"));
        definition.getRequiredGuards().add(guard("OPENAPI_ORCHESTRATION_CANCELLABLE", "集成编排当前可取消"));
        definition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["executionId", "applicationClientId", "reason"],
                  "additionalProperties": false,
                  "properties": {
                    "executionId": {"type": "string"},
                    "applicationClientId": {"type": "string"},
                    "reason": {"type": "string"}
                  }
                }
                """);
        return definition;
    }

    private ActionDefinition stateChangingInvocation() {
        ActionDefinition definition = base(
                "integration.invocation.stateChanging",
                "INTEGRATION_ROUTE",
                ActionExecutionMode.SYNC);
        definition.setDanger(true);
        definition.setRequiresConfirmation(true);
        definition.setTargetStatus("UPDATED");
        definition.setTargetStatusGroup("BUSINESS");
        definition.getDefaultRefreshScopes().addAll(List.of("document", "actions", "timeline", "relatedTables"));
        definition.getRequiredGuards().add(guard("OPENAPI_STATE_CHANGING_ALLOWED", "状态变更调用当前允许执行"));
        definition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["routeKey", "environment", "operation", "admission", "payload"],
                  "additionalProperties": false,
                  "properties": {
                    "routeKey": {"type": "string"},
                    "environment": {"type": "string"},
                    "operation": {"type": "string"},
                    "idempotencyKey": {"type": "string"},
                    "admission": {"type": "object"},
                    "payload": {}
                  }
                }
                """);
        return definition;
    }

    private ActionDefinition callbackSignal() {
        ActionDefinition definition = base(
                "integration.callback.signal",
                "OPENAPI_CALLBACK_INBOX",
                ActionExecutionMode.SIGNAL);
        definition.setTargetStatus("SIGNALED");
        definition.setTargetStatusGroup("IN_PROGRESS");
        definition.getDefaultRefreshScopes().addAll(List.of("document", "actions", "timeline", "relatedTables"));
        definition.getRequiredGuards().add(guard("OPENAPI_CALLBACK_SIGNALABLE", "回调收件箱当前可发送信号"));
        definition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["inboxId"],
                  "additionalProperties": false,
                  "properties": {
                    "inboxId": {"type": "string"}
                  }
                }
                """);
        return definition;
    }

    private ActionDefinition base(String actionType, String targetType, ActionExecutionMode mode) {
        ActionDefinition definition = new ActionDefinition();
        definition.setActionType(actionType);
        definition.setOwnerService(OWNER_SERVICE);
        definition.setTargetType(targetType);
        definition.setDisplayName(actionType);
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
}
