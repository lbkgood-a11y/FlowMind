package com.triobase.common.action.model;

import com.triobase.common.action.enums.ActionStatus;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class GlobalActionResult {
    private String actionId;
    private String actionType;
    private ActionStatus status;
    private ActionTarget target;
    private String ownerService;
    private String ownerExecutionRef;
    private Map<String, Object> ownerExecutionMetadata = new LinkedHashMap<>();
    private boolean retryable;
    private String message;
    private String targetStatus;
    private String targetStatusGroup;
    private List<String> refreshScopes = new ArrayList<>();
    private Map<String, Object> data = new LinkedHashMap<>();
    private List<ActionError> errors = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public boolean terminal() {
        return status != null && status.terminal();
    }

    public static GlobalActionResult from(GlobalActionRequest request) {
        GlobalActionResult response = new GlobalActionResult();
        if (request != null) {
            response.setActionId(request.getActionId());
            response.setActionType(request.getActionType());
            response.setTarget(request.getTarget());
            response.setOwnerService(
                    request.getTarget() != null ? request.getTarget().getOwnerService() : null);
        }
        return response;
    }
}
