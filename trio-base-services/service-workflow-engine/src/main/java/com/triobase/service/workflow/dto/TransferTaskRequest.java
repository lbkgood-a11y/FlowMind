package com.triobase.service.workflow.dto;

import lombok.Data;

@Data
public class TransferTaskRequest {
    private String operationId;
    private String newAssigneeId;
    private String newAssigneeName;
}
