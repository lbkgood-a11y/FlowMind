package com.triobase.common.action.runtime;

import com.triobase.common.action.model.GlobalActionResult;

public final class OwnerActionIdempotencyResult {

    private final GlobalActionResult result;
    private final boolean duplicate;

    private OwnerActionIdempotencyResult(GlobalActionResult result, boolean duplicate) {
        this.result = result;
        this.duplicate = duplicate;
    }

    public static OwnerActionIdempotencyResult fresh(GlobalActionResult result) {
        return new OwnerActionIdempotencyResult(result, false);
    }

    public static OwnerActionIdempotencyResult duplicate(GlobalActionResult result) {
        return new OwnerActionIdempotencyResult(result, true);
    }

    public GlobalActionResult result() {
        return result;
    }

    public boolean duplicate() {
        return duplicate;
    }
}
