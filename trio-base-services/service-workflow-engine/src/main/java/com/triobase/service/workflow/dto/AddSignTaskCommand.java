package com.triobase.service.workflow.dto;

import lombok.Data;

@Data
public class AddSignTaskCommand {
    private String operationId;
    private String sourceTaskId;
    private String addedTaskId;
    private String operatorId;
    private String operatorName;
    private String targetUserId;
    private String targetUserName;
    private String traceId;
}
