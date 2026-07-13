package com.triobase.service.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskResponse {
    private String id;
    private String processInstanceId;
    private String processKey;
    private String processName;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String title;
    private String status;
    private String assigneeId;
    private String assigneeName;
    private String assigneeType;
    private String comment;
    private LocalDateTime claimedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
