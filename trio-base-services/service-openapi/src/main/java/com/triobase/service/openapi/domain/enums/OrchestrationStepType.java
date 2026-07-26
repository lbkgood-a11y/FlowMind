package com.triobase.service.openapi.domain.enums;

public enum OrchestrationStepType {
    INVOKE,
    OWNER_ACTION,
    TRANSFORM,
    BRANCH,
    PARALLEL,
    WAIT,
    COMPENSATE,
    END
}
