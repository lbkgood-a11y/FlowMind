package com.triobase.service.action.service;

import com.triobase.common.action.model.ActionError;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;

import java.util.List;

public record ActionPolicyDecision(boolean allowed,
                                   AuthorizationDecisionResponse response,
                                   List<ActionError> errors) {

    public static ActionPolicyDecision allowed(AuthorizationDecisionResponse response) {
        return new ActionPolicyDecision(true, response, List.of());
    }

    public static ActionPolicyDecision denied(AuthorizationDecisionResponse response,
                                              List<ActionError> errors) {
        return new ActionPolicyDecision(false, response, errors);
    }
}
