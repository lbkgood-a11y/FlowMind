package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class BindFormProcessRequest {
    private String processKey;
    private String processInstanceId;
    private String workflowStatus;
    private String traceId;
}
