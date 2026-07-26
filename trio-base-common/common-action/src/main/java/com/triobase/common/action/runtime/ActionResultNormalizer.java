package com.triobase.common.action.runtime;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.core.util.StringHelpers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class ActionResultNormalizer {

    private ActionResultNormalizer() {
    }

    public static GlobalActionResult normalize(ActionDefinition definition,
                                               GlobalActionRequest request,
                                               GlobalActionResult ownerResult) {
        GlobalActionResult result = ownerResult != null ? ownerResult : new GlobalActionResult();
        ActionStatus status = normalizeStatus(definition, result.getStatus());
        result.setActionId(StringHelpers.firstNonBlank(result.getActionId(), request.getActionId()));
        result.setActionType(definition.getActionType());
        result.setStatus(status);
        if (result.getTarget() == null) {
            result.setTarget(request.getTarget());
        }
        result.setOwnerService(StringHelpers.firstNonBlank(result.getOwnerService(), definition.getOwnerService()));
        result.setCreatedAt(result.getCreatedAt() != null ? result.getCreatedAt() : Instant.now());
        result.setUpdatedAt(Instant.now());
        if ((result.getRefreshScopes() == null || result.getRefreshScopes().isEmpty())
                && definition.getDefaultRefreshScopes() != null) {
            result.setRefreshScopes(definition.getDefaultRefreshScopes());
        }
        result.setRefreshScopes(result.getRefreshScopes() != null
                ? new ArrayList<>(result.getRefreshScopes())
                : new ArrayList<>());
        result.setData(result.getData() != null
                ? new LinkedHashMap<>(result.getData())
                : new LinkedHashMap<>());
        result.setOwnerExecutionMetadata(result.getOwnerExecutionMetadata() != null
                ? new LinkedHashMap<>(result.getOwnerExecutionMetadata())
                : new LinkedHashMap<>());
        if (result.getTargetStatus() == null) {
            result.setTargetStatus(definition.getTargetStatus());
        }
        if (result.getTargetStatusGroup() == null) {
            result.setTargetStatusGroup(definition.getTargetStatusGroup());
        }
        result.getData().put("targetStatus", result.getTargetStatus());
        result.getData().put("targetStatusGroup", result.getTargetStatusGroup());
        result.getData().put("refreshScopes", result.getRefreshScopes());
        result.getData().put("ownerExecutionMetadata", result.getOwnerExecutionMetadata());
        return result;
    }

    public static ActionStatus normalizeStatus(ActionDefinition definition, ActionStatus status) {
        if (status != null && List.of(ActionStatus.ACCEPTED, ActionStatus.RUNNING,
                ActionStatus.SUCCEEDED, ActionStatus.FAILED, ActionStatus.REJECTED,
                ActionStatus.CANCELLED).contains(status)) {
            return status;
        }
        ActionExecutionMode mode = definition.getExecutionMode();
        if (mode == ActionExecutionMode.ASYNC
                || mode == ActionExecutionMode.WORKFLOW
                || mode == ActionExecutionMode.SIGNAL) {
            return ActionStatus.ACCEPTED;
        }
        return ActionStatus.SUCCEEDED;
    }
}
