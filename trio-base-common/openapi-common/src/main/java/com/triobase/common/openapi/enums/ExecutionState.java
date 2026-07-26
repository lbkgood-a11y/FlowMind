package com.triobase.common.openapi.enums;

public enum ExecutionState {
    ACCEPTED,
    RUNNING,
    WAITING_CALLBACK,
    SUCCEEDED,
    FAILED,
    COMPENSATING,
    COMPENSATED,
    CANCELLED,
    QUARANTINED
}
