package com.triobase.common.action.runtime;

import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;

import java.util.function.Supplier;

public final class NoopOwnerActionIdempotencyStore implements OwnerActionIdempotencyStore {

    public static final NoopOwnerActionIdempotencyStore INSTANCE = new NoopOwnerActionIdempotencyStore();

    private NoopOwnerActionIdempotencyStore() {
    }

    @Override
    public OwnerActionIdempotencyResult execute(GlobalActionRequest request,
                                               Supplier<GlobalActionResult> dispatcher) {
        return OwnerActionIdempotencyResult.fresh(dispatcher.get());
    }
}
