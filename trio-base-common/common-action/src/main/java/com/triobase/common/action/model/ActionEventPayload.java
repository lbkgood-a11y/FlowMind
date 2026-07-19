package com.triobase.common.action.model;

import com.triobase.common.action.enums.ActionEventType;
import com.triobase.common.action.enums.ActionStatus;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ActionEventPayload {
    private String eventId;
    private String actionId;
    private ActionEventType eventType;
    private ActionStatus status;
    private String message;
    private Instant occurredAt;
    private Map<String, Object> data = new LinkedHashMap<>();
}
