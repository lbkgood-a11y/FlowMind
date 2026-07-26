package com.triobase.common.action.runtime;

import com.triobase.common.action.model.ActionEventPayload;

import java.util.ArrayList;
import java.util.List;

public class InMemoryOwnerActionAuditSink implements OwnerActionAuditSink {

    private static final int DEFAULT_MAX_EVENTS = 1000;

    private final int maxEvents;
    private final List<ActionEventPayload> events = new ArrayList<>();

    public InMemoryOwnerActionAuditSink() {
        this(DEFAULT_MAX_EVENTS);
    }

    public InMemoryOwnerActionAuditSink(int maxEvents) {
        this.maxEvents = Math.max(1, maxEvents);
    }

    @Override
    public synchronized void emit(ActionEventPayload event) {
        events.add(event);
        while (events.size() > maxEvents) {
            events.remove(0);
        }
    }

    public synchronized List<ActionEventPayload> events() {
        return new ArrayList<>(events);
    }
}
