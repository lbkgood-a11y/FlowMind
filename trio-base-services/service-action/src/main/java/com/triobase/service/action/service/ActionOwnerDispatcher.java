package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.service.action.entity.ActionExecution;

@FunctionalInterface
public interface ActionOwnerDispatcher {

    GlobalActionResult dispatch(ActionDefinition definition,
                                GlobalActionRequest request,
                                ActionExecution execution);
}
