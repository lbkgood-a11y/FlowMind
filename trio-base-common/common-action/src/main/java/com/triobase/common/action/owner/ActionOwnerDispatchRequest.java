package com.triobase.common.action.owner;

import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionTarget;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ActionOwnerDispatchRequest {
    private String actionId;
    private String actionType;
    private ActionSource source;
    private ActionActor actor = new ActionActor();
    private ActionTarget target = new ActionTarget();
    private Map<String, Object> payload = new LinkedHashMap<>();
    private ActionContext context = new ActionContext();
    private String idempotencyKey;
    private ActionExecutionMode executionMode;
    private String ownerService;
}
