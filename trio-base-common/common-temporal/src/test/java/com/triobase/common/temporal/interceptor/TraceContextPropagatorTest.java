package com.triobase.common.temporal.interceptor;

import com.triobase.common.core.trace.TraceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceContextPropagatorTest {

    private final TraceContextPropagator propagator = new TraceContextPropagator();

    @AfterEach
    void clearTrace() {
        TraceUtil.clear();
    }

    @Test
    void serializesAndRestoresTraceId() {
        TraceUtil.setTraceId("trace-001");
        Object restored = propagator.deserializeContext(
                propagator.serializeContext(propagator.getCurrentContext()));

        TraceUtil.clear();
        propagator.setCurrentContext(restored);

        assertEquals("trace-001", TraceUtil.getTraceId());
    }

    @Test
    void emptyContextClearsThreadTrace() {
        TraceUtil.setTraceId("stale-trace");
        propagator.setCurrentContext(null);

        assertNull(TraceUtil.getTraceId());
    }
}
