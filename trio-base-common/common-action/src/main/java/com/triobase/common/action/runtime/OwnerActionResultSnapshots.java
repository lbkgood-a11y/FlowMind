package com.triobase.common.action.runtime;

import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;

final class OwnerActionResultSnapshots {

    private OwnerActionResultSnapshots() {
    }

    static GlobalActionResult copy(GlobalActionResult source) {
        if (source == null) {
            return null;
        }
        GlobalActionResult copy = new GlobalActionResult();
        copy.setActionId(source.getActionId());
        copy.setActionType(source.getActionType());
        copy.setStatus(source.getStatus());
        copy.setTarget(copyTarget(source.getTarget()));
        copy.setOwnerService(source.getOwnerService());
        copy.setOwnerExecutionRef(source.getOwnerExecutionRef());
        copy.setOwnerExecutionMetadata(source.getOwnerExecutionMetadata() != null
                ? new LinkedHashMap<>(source.getOwnerExecutionMetadata())
                : new LinkedHashMap<>());
        copy.setRetryable(source.isRetryable());
        copy.setMessage(source.getMessage());
        copy.setTargetStatus(source.getTargetStatus());
        copy.setTargetStatusGroup(source.getTargetStatusGroup());
        copy.setRefreshScopes(source.getRefreshScopes() != null
                ? new ArrayList<>(source.getRefreshScopes())
                : new ArrayList<>());
        copy.setData(source.getData() != null
                ? new LinkedHashMap<>(source.getData())
                : new LinkedHashMap<>());
        copy.setErrors(source.getErrors() != null
                ? new ArrayList<>(source.getErrors())
                : new ArrayList<>());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private static ActionTarget copyTarget(ActionTarget source) {
        if (source == null) {
            return null;
        }
        ActionTarget copy = new ActionTarget();
        copy.setType(source.getType());
        copy.setId(source.getId());
        copy.setOwnerService(source.getOwnerService());
        copy.setTenantId(source.getTenantId());
        copy.setVersion(source.getVersion());
        copy.setAttributes(source.getAttributes() != null
                ? new LinkedHashMap<>(source.getAttributes())
                : new LinkedHashMap<>());
        return copy;
    }
}
