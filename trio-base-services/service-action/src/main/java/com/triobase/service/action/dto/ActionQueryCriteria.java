package com.triobase.service.action.dto;

import lombok.Data;

@Data
public class ActionQueryCriteria {
    private int page = 1;
    private int size = 20;
    private String tenantId;
    private String actionType;
    private String actorId;
    private String actorType;
    private String source;
    private String targetType;
    private String targetId;
    private String status;
    private String traceId;
    private String correlationId;
    private String idempotencyKey;
}
