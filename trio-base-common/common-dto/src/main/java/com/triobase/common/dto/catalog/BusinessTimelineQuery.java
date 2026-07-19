package com.triobase.common.dto.catalog;

import lombok.Data;

import java.time.Instant;

@Data
public class BusinessTimelineQuery {
    private String tenantId;
    private String targetType;
    private String targetId;
    private String actionId;
    private String actionType;
    private String actionStatus;
    private String ownerExecutionRef;
    private String eventSource;
    private String traceId;
    private String correlationId;
    private String actorId;
    private String eventType;
    private Instant startTime;
    private Instant endTime;
    private int page = 1;
    private int size = 20;
}
