package com.triobase.common.action.runtime;

import com.triobase.common.action.model.ActionEventPayload;

public interface OwnerActionAuditSink {

    void emit(ActionEventPayload event);
}
