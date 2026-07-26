package com.triobase.common.action.runtime;

import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.util.ActionIdempotencyKeys;
import com.triobase.common.core.util.StringHelpers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class InMemoryOwnerActionIdempotencyStore implements OwnerActionIdempotencyStore {

    private static final String GLOBAL_TENANT = "GLOBAL";

    private final ConcurrentMap<String, CompletableFuture<GlobalActionResult>> results = new ConcurrentHashMap<>();

    @Override
    public OwnerActionIdempotencyResult execute(GlobalActionRequest request,
                                               Supplier<GlobalActionResult> dispatcher) {
        String key = scopedKey(request);
        if (key == null) {
            return OwnerActionIdempotencyResult.fresh(dispatcher.get());
        }

        CompletableFuture<GlobalActionResult> reservation = new CompletableFuture<>();
        CompletableFuture<GlobalActionResult> existing = results.putIfAbsent(key, reservation);
        if (existing != null) {
            return OwnerActionIdempotencyResult.duplicate(join(existing));
        }

        try {
            GlobalActionResult result = dispatcher.get();
            reservation.complete(OwnerActionResultSnapshots.copy(result));
            return OwnerActionIdempotencyResult.fresh(result);
        } catch (RuntimeException exception) {
            results.remove(key, reservation);
            reservation.completeExceptionally(exception);
            throw exception;
        }
    }

    private GlobalActionResult join(CompletableFuture<GlobalActionResult> future) {
        try {
            return OwnerActionResultSnapshots.copy(future.join());
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private String scopedKey(GlobalActionRequest request) {
        if (request == null) {
            return null;
        }
        String idempotencyKey = ActionIdempotencyKeys.normalize(request.getIdempotencyKey());
        if (idempotencyKey == null) {
            return null;
        }
        return ActionIdempotencyKeys.scoped(resolveTenantId(request), request.getActionType(), idempotencyKey);
    }

    private String resolveTenantId(GlobalActionRequest request) {
        ActionContext context = request.getContext();
        ActionTarget target = request.getTarget();
        ActionActor actor = request.getActor();
        String tenant = StringHelpers.firstNonBlank(
                context != null ? context.getTenantId() : null,
                target != null ? target.getTenantId() : null,
                actor != null ? actor.getTenantId() : null);
        return tenant != null ? tenant : GLOBAL_TENANT;
    }
}
