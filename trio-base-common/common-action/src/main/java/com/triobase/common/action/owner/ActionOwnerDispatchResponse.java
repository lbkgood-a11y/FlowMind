package com.triobase.common.action.owner;

import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ActionOwnerDispatchResponse {
    private String actionId;
    private ActionStatus status;
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
}
