package com.triobase.common.action.owner;

public interface ActionOwnerGuard {

    String guardCode();

    ActionOwnerGuardResponse evaluate(ActionOwnerDispatchRequest request);
}
