package com.triobase.service.workflow.dto;

import lombok.Data;

@Data
public class RejectTaskCommand {
    private String operationId;
    private String taskId;
    private String userId;
    private String userName;
    private String targetNodeId;
    private String comment;
    private String traceId;
}
