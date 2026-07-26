package com.triobase.service.apiruntime.action;

public class OwnerActionDispatchException extends RuntimeException {

    private final boolean retryable;
    private final Integer externalStatus;

    public OwnerActionDispatchException(boolean retryable, Integer externalStatus, String message) {
        super(message);
        this.retryable = retryable;
        this.externalStatus = externalStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getExternalStatus() {
        return externalStatus;
    }
}
