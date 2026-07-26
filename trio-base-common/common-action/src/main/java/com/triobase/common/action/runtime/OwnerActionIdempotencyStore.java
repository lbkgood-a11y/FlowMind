package com.triobase.common.action.runtime;

import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;

import java.util.function.Supplier;

public interface OwnerActionIdempotencyStore {

    OwnerActionIdempotencyResult execute(GlobalActionRequest request, Supplier<GlobalActionResult> dispatcher);
}
