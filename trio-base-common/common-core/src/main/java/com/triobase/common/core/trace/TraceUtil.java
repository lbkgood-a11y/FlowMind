package com.triobase.common.core.trace;

import org.slf4j.MDC;

/**
 * TraceId 工具 — 铁律 8 要求 TraceId 全链路贯通。
 * 从 SLF4J MDC 中读取 X-B3-TraceId，供 Temporal ContextPropagationInterceptor 透传。
 */
public final class TraceUtil {

    public static final String TRACE_ID_KEY = "X-B3-TraceId";

    private TraceUtil() {
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
