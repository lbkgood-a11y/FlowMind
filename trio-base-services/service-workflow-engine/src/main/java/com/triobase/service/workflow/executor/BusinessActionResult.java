package com.triobase.service.workflow.executor;

import java.util.Map;

public record BusinessActionResult(
        boolean success,
        String resultCode,
        String message,
        String businessId,
        Map<String, Object> data) {

    public static BusinessActionResult succeeded(String resultCode, String businessId,
                                                 Map<String, Object> data) {
        return new BusinessActionResult(true, resultCode, null, businessId, data);
    }

    public static BusinessActionResult failed(String resultCode, String message) {
        return new BusinessActionResult(false, resultCode, message, null, Map.of());
    }
}
