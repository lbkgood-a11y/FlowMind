package com.triobase.service.workflow.dto;

import lombok.Data;

@Data
public class TaskActionCommand {
    private String operationId;
    private String taskId;
    private String action;
    private String userId;
    private String userName;
    private String comment;
    private String traceId;
}
