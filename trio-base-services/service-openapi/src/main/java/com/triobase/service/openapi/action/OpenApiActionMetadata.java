package com.triobase.service.openapi.action;

import com.triobase.service.openapi.domain.entity.AuditEvent;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.entity.ExecutionStepAttempt;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;

public final class OpenApiActionMetadata {

    private OpenApiActionMetadata() {
    }

    public static void apply(IntegrationExecution execution) {
        OpenApiActionExecutionContext.Snapshot snapshot = OpenApiActionExecutionContext.current();
        if (execution == null || snapshot == null) {
            return;
        }
        execution.setActionId(snapshot.actionId());
        execution.setActionType(snapshot.actionType());
        execution.setActionSource(snapshot.source());
        execution.setActionActorType(snapshot.actorType());
        execution.setActionActorId(snapshot.actorId());
        execution.setActionActorName(snapshot.actorName());
        execution.setActionTraceId(snapshot.traceId());
        execution.setActionCorrelationId(snapshot.correlationId());
    }

    public static void apply(ExecutionStepAttempt attempt) {
        OpenApiActionExecutionContext.Snapshot snapshot = OpenApiActionExecutionContext.current();
        if (attempt == null || snapshot == null) {
            return;
        }
        attempt.setActionId(snapshot.actionId());
        attempt.setActionType(snapshot.actionType());
        attempt.setActionSource(snapshot.source());
        attempt.setActionActorType(snapshot.actorType());
        attempt.setActionActorId(snapshot.actorId());
        attempt.setActionActorName(snapshot.actorName());
        attempt.setActionTraceId(snapshot.traceId());
        attempt.setActionCorrelationId(snapshot.correlationId());
    }

    public static void apply(CallbackInbox inbox) {
        OpenApiActionExecutionContext.Snapshot snapshot = OpenApiActionExecutionContext.current();
        if (inbox == null || snapshot == null) {
            return;
        }
        inbox.setActionId(snapshot.actionId());
        inbox.setActionType(snapshot.actionType());
        inbox.setActionSource(snapshot.source());
        inbox.setActionActorType(snapshot.actorType());
        inbox.setActionActorId(snapshot.actorId());
        inbox.setActionActorName(snapshot.actorName());
        inbox.setActionTraceId(snapshot.traceId());
        inbox.setActionCorrelationId(snapshot.correlationId());
    }

    public static void apply(AuditEvent event) {
        OpenApiActionExecutionContext.Snapshot snapshot = OpenApiActionExecutionContext.current();
        if (event == null || snapshot == null) {
            return;
        }
        event.setActionId(snapshot.actionId());
        event.setActionType(snapshot.actionType());
        event.setActionSource(snapshot.source());
        event.setActionActorType(snapshot.actorType());
        event.setActionActorId(snapshot.actorId());
        event.setActionActorName(snapshot.actorName());
        event.setActionTraceId(snapshot.traceId());
        event.setActionCorrelationId(snapshot.correlationId());
    }
}
