package com.triobase.service.openapi.temporal;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenApiTemporalContext {

    public static final String TRACE_ID = "traceId";
    public static final String TENANT_ID = "tenantId";
    public static final String APPLICATION_CLIENT_ID = "applicationClientId";
    public static final String CALLER_ID = "callerId";
    public static final String RELEASE_ID = "releaseId";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";

    private static final ThreadLocal<Map<String, String>> CURRENT = new ThreadLocal<>();

    private OpenApiTemporalContext() {
    }

    public static void set(Map<String, String> context) {
        CURRENT.set(Map.copyOf(context));
    }

    public static Map<String, String> get() {
        Map<String, String> context = CURRENT.get();
        return context == null ? Map.of() : context;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Map<String, String> of(String traceId, String tenantId, String applicationClientId,
                                         String callerId, String releaseId, String idempotencyKey) {
        Map<String, String> context = new LinkedHashMap<>();
        put(context, TRACE_ID, traceId);
        put(context, TENANT_ID, tenantId);
        put(context, APPLICATION_CLIENT_ID, applicationClientId);
        put(context, CALLER_ID, callerId);
        put(context, RELEASE_ID, releaseId);
        put(context, IDEMPOTENCY_KEY, idempotencyKey);
        return Map.copyOf(context);
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
