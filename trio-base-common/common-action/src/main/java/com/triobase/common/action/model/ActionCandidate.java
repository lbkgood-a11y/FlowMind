package com.triobase.common.action.model;

import com.triobase.common.action.definition.ActionConfirmation;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ActionCandidate {
    private String candidateId;
    private String actionType;
    private ActionSource source;
    private ActionActor actor = new ActionActor();
    private ActionTarget target = new ActionTarget();
    private Map<String, Object> payload = new LinkedHashMap<>();
    private ActionContext context = new ActionContext();
    private String idempotencyKey;
    private ActionExecutionMode executionMode;
    private String reason;
    private String proposedBy;
    private boolean requiresConfirmation;
    private ActionConfirmation confirmation;
    private Instant createdAt;

    public GlobalActionRequest toActionRequest() {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionType(actionType);
        request.setSource(source);
        request.setActor(actor);
        request.setTarget(target);
        request.setPayload(payload != null ? payload : new LinkedHashMap<>());
        request.setContext(context);
        request.setIdempotencyKey(idempotencyKey);
        request.setExecutionMode(executionMode);
        return request;
    }
}
