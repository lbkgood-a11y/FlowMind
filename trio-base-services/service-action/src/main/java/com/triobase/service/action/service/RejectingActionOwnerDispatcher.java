package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.service.action.entity.ActionExecution;
import com.triobase.service.action.exception.ActionRuntimeException;

public class RejectingActionOwnerDispatcher implements ActionOwnerDispatcher {

    @Override
    public GlobalActionResult dispatch(ActionDefinition definition,
                                       GlobalActionRequest request,
                                       ActionExecution execution) {
        throw new ActionRuntimeException(
                50242,
                ActionErrorCategory.DISPATCH,
                "ACTION_OWNER_DISPATCHER_NOT_CONFIGURED");
    }
}
