package com.triobase.common.action.owner;

import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;

public interface ActionOwnerExecutor {

    String actionType();

    GlobalActionResult execute(GlobalActionRequest request);
}
