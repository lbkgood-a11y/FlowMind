package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class RuntimeRetryWorkflowRequest {
    private String actionCode;
    private String idempotencyKey;
}
