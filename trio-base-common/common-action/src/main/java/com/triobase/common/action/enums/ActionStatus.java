package com.triobase.common.action.enums;

import java.util.EnumSet;
import java.util.Set;

public enum ActionStatus {
    CREATED,
    VALIDATING,
    REJECTED,
    AUTHORIZED,
    ACCEPTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    COMPENSATING,
    COMPENSATED;

    private static final Set<ActionStatus> TERMINAL = EnumSet.of(
            REJECTED, SUCCEEDED, FAILED, CANCELLED, COMPENSATED);

    public boolean terminal() {
        return TERMINAL.contains(this);
    }
}
