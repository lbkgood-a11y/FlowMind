package com.triobase.service.action.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActionEventResponse {
    private String eventId;
    private String actionId;
    private String tenantId;
    private String eventType;
    private String status;
    private Integer sequenceNo;
    private String message;
    private String eventDataJson;
    private String traceId;
    private LocalDateTime occurredAt;
}
