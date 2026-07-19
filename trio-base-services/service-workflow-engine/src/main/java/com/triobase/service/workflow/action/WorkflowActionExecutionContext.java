package com.triobase.service.workflow.action;

import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import org.springframework.util.StringUtils;

public final class WorkflowActionExecutionContext {

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private WorkflowActionExecutionContext() {
    }

    public static void set(ActionOwnerDispatchRequest request) {
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
            String correlationId
    ) {
        private static Snapshot from(ActionOwnerDispatchRequest request) {
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
            return StringUtils.hasText(value) ? value.trim() : null;
        }
    }
}
