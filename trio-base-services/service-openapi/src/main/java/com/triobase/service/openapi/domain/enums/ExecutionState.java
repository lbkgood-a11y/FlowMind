package com.triobase.service.openapi.domain.enums;

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
