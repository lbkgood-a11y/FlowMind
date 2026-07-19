package com.triobase.service.openapi.action;

import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;

public final class OpenApiActionExecutionContext {

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private OpenApiActionExecutionContext() {
    }

    public static void set(ActionOwnerDispatchRequest request) {
        CURRENT.set(Snapshot.from(request));
    }

    public static Snapshot current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record Snapshot(
            String actionId,
            String actionType,
            String source,
            String actorType,
            String actorId,
            String actorName,
            String traceId,
            String correlationId) {

        private static Snapshot from(ActionOwnerDispatchRequest request) {
            ActionActor actor = request != null ? request.getActor() : null;
            ActionContext context = request != null ? request.getContext() : null;
            return new Snapshot(
                    request != null ? text(request.getActionId()) : null,
                    request != null ? text(request.getActionType()) : null,
                    request != null && request.getSource() != null ? request.getSource().name() : null,
                    actor != null && actor.getType() != null ? actor.getType().name() : null,
                    actor != null ? text(actor.getId()) : null,
                    actor != null ? text(actor.getDisplayName()) : null,
                    context != null ? text(context.getTraceId()) : null,
                    context != null ? text(context.getCorrelationId()) : null);
        }

        private static String text(String value) {
            return value != null && !value.isBlank() ? value.trim() : null;
        }
    }
}
