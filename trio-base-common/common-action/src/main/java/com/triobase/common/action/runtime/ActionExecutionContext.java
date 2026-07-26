package com.triobase.common.action.runtime;

import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.GlobalActionRequest;

/**
 * Thread-local snapshot of the executing action, available to downstream business
 * code without threading the request through every method signature.
 */
public final class ActionExecutionContext {

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private ActionExecutionContext() {
    }

    public static void set(GlobalActionRequest request) {
        if (request == null) {
            clear();
            return;
        }
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

        public static Snapshot from(GlobalActionRequest request) {
            ActionActor actor = request.getActor();
            ActionContext context = request.getContext();
            ActionSource source = request.getSource();
            return new Snapshot(
                    text(request.getActionId()),
                    text(request.getActionType()),
                    source != null ? source.name() : null,
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
