package com.triobase.service.workflow.dto;

import lombok.Data;

@Data
public class ApproveTaskRequest {
    private String operationId;
    private String action;   // APPROVE / REJECT
    private String comment;
}
