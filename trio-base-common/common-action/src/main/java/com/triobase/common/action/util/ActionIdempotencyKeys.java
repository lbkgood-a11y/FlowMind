package com.triobase.common.action.util;

public final class ActionIdempotencyKeys {

    public static final int MAX_LENGTH = 256;

    private ActionIdempotencyKeys() {
    }

    public static String normalize(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("idempotencyKey is longer than " + MAX_LENGTH);
        }
        return trimmed;
    }

    public static String require(String idempotencyKey) {
        String normalized = normalize(idempotencyKey);
        if (normalized == null) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        return normalized;
    }

    public static String scoped(String tenantId, String actionType, String idempotencyKey) {
        return safe(tenantId) + ":" + safe(ActionTypeValidator.requireValid(actionType))
                + ":" + require(idempotencyKey);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "GLOBAL" : value.trim();
    }
}
