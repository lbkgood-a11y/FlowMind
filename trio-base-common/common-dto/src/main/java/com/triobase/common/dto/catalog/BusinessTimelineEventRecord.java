package com.triobase.common.dto.catalog;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class BusinessTimelineEventRecord {
    private String eventId;
    private String eventSource;
    private String tenantId;
    private String targetType;
    private String targetId;
    private String eventType;
    private String displayName;
    private String actorId;
    private String actorName;
    private String actionId;
    private String actionType;
    private String actionStatus;
    private String ownerService;
    private String ownerExecutionRef;
    private String traceId;
    private String correlationId;
    private Instant occurredAt;
    private Map<String, Object> summary = new LinkedHashMap<>();
}
