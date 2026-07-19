package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;

public interface ActionOwnerGuardChecker {

    ActionOwnerGuardResponse check(ActionDefinition definition, GlobalActionRequest request);
}
