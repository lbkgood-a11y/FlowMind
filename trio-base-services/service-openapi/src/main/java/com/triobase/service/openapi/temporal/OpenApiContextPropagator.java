package com.triobase.service.openapi.temporal;

import io.temporal.api.common.v1.Payload;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenApiContextPropagator implements ContextPropagator {

    private static final DataConverter CONVERTER = DefaultDataConverter.STANDARD_INSTANCE;

    @Override
    public String getName() {
        return "triobase-openapi-context";
    }

    @Override
    public Map<String, Payload> serializeContext(Object context) {
        if (!(context instanceof Map<?, ?> values)) {
            return Map.of();
        }
        Map<String, Payload> payloads = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key instanceof String name && value instanceof String text && !text.isBlank()) {
                payloads.put(name, CONVERTER.toPayload(text).orElseThrow());
            }
        });
        return payloads;
    }

    @Override
    public Object deserializeContext(Map<String, Payload> context) {
        Map<String, String> values = new LinkedHashMap<>();
        context.forEach((key, payload) -> values.put(
                key, CONVERTER.fromPayload(payload, String.class, String.class)));
        return Map.copyOf(values);
    }

    @Override
    public Object getCurrentContext() {
        return OpenApiTemporalContext.get();
    }

    @Override
    public void setCurrentContext(Object context) {
        OpenApiTemporalContext.clear();
        if (context instanceof Map<?, ?> values) {
            Map<String, String> converted = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (key instanceof String name && value instanceof String text) {
                    converted.put(name, text);
                }
            });
            OpenApiTemporalContext.set(converted);
        }
    }
}
