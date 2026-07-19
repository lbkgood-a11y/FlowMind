package com.triobase.common.action.owner;

public interface ActionOwnerExecutor {

    String actionType();

    ActionOwnerDispatchResponse execute(ActionOwnerDispatchRequest request);
}
