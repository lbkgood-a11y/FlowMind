package com.triobase.common.action.runtime;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;

@FunctionalInterface
public interface OwnerActionGuardEvaluator {

    ActionOwnerGuardResponse check(ActionDefinition definition, GlobalActionRequest request);

    static OwnerActionGuardEvaluator allowAll() {
        return (definition, request) -> ActionOwnerGuardResponse.allowed("ACTION_OWNER_GUARD_NOT_CONFIGURED");
    }
}
