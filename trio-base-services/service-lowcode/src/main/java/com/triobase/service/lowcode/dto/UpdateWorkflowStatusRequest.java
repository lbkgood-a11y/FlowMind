package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class UpdateWorkflowStatusRequest {
    private String workflowStatus;
    private String traceId;
}
