package com.triobase.common.action.util;

import java.util.UUID;

public final class ActionCorrelationIds {

    private static final String ACTION_PREFIX = "act_";
    private static final String CORRELATION_PREFIX = "corr_";
    private static final String REQUEST_PREFIX = "req_";

    private ActionCorrelationIds() {
    }

    public static String newActionId() {
        return ACTION_PREFIX + compactUuid();
    }

    public static String newCorrelationId() {
        return CORRELATION_PREFIX + compactUuid();
    }

    public static String newRequestId() {
        return REQUEST_PREFIX + compactUuid();
    }

    public static String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first.trim() : fallback;
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
