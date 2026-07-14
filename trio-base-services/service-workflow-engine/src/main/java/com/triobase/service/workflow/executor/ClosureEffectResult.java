package com.triobase.service.workflow.executor;

import java.util.Map;

public record ClosureEffectResult(
        boolean success,
        String resultCode,
        String message,
        String failureCategory,
        Map<String, Object> data) {

    public static ClosureEffectResult succeeded(String resultCode, Map<String, Object> data) {
        return new ClosureEffectResult(true, resultCode, null, null, data);
    }

    public static ClosureEffectResult failed(String resultCode, String failureCategory,
                                             String message) {
        return new ClosureEffectResult(false, resultCode, message, failureCategory, Map.of());
    }
}
