package com.triobase.common.action.runtime;

import com.triobase.common.action.model.ActionEventPayload;

public final class NoopOwnerActionAuditSink implements OwnerActionAuditSink {

    public static final NoopOwnerActionAuditSink INSTANCE = new NoopOwnerActionAuditSink();

    private NoopOwnerActionAuditSink() {
    }

    @Override
    public void emit(ActionEventPayload event) {
        // no-op
    }
}
