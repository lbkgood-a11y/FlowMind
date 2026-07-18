package com.triobase.service.lowcode.service;

import lombok.Getter;

@Getter
public class WorkflowLaunchException extends RuntimeException {

    private final String errorCode;
    private final boolean versionConflict;

    public WorkflowLaunchException(String errorCode, String message, boolean versionConflict) {
        super(message);
        this.errorCode = errorCode;
        this.versionConflict = versionConflict;
    }
}
