package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class RuntimeActionResponse {
    private String actionCode;
    private String status;
    private FormInstanceResponse formInstance;
    private RuntimeWorkflowResponse workflow;
    private boolean retryable;
    private String errorCode;
    private String message;
}
