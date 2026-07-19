package com.triobase.common.action.definition;

import com.triobase.common.action.enums.ActionAuditLevel;
import com.triobase.common.action.enums.ActionExecutionMode;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
public class ActionDefinition {
    private String actionType;
    private String ownerService;
    private String targetType;
    private String displayName;
    private String description;
    private String payloadSchemaJson;
    private String resultSchemaJson;
    private String requiredPermission;
    private boolean visibleByDefault = true;
    private boolean danger;
    private boolean primary;
    private List<ActionGuardRequirement> requiredGuards = new ArrayList<>();
    private Set<String> allowedStates = new LinkedHashSet<>();
    private ActionExecutionMode executionMode = ActionExecutionMode.SYNC;
    private boolean requiresConfirmation;
    private ActionConfirmation confirmation;
    private ActionAuditLevel auditLevel = ActionAuditLevel.NORMAL;
    private List<ActionSensitivePath> sensitivePayloadPaths = new ArrayList<>();
    private ActionRetryPolicy retryPolicy;
    private String compensationActionType;
    private String targetStatus;
    private String targetStatusGroup;
    private List<String> defaultRefreshScopes = new ArrayList<>();
}
