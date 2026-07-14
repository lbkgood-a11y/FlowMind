package com.triobase.common.temporal.interceptor;

import com.triobase.common.core.trace.TraceUtil;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;

import java.util.Map;

public final class TraceContextPropagator implements ContextPropagator {

    private static final String NAME = "triobase-trace-context";
    private static final String TRACE_KEY = "traceId";
    private static final DataConverter DATA_CONVERTER = DefaultDataConverter.STANDARD_INSTANCE;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, Payload> serializeContext(Object context) {
        if (!(context instanceof String traceId) || traceId.isBlank()) {
            return Map.of();
        }
        return Map.of(TRACE_KEY, DATA_CONVERTER.toPayload(traceId).orElseThrow());
    }

    @Override
    public Object deserializeContext(Map<String, Payload> context) {
        Payload payload = context.get(TRACE_KEY);
        return payload != null
                ? DATA_CONVERTER.fromPayload(payload, String.class, String.class)
                : null;
    }

    @Override
    public Object getCurrentContext() {
        return TraceUtil.getTraceId();
    }

    @Override
    public void setCurrentContext(Object context) {
        TraceUtil.clear();
        if (context instanceof String traceId) {
            TraceUtil.setTraceId(traceId);
        }
    }
}
