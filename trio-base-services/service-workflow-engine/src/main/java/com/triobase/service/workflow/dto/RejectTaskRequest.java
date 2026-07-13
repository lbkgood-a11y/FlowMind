package com.triobase.service.workflow.dto;

import lombok.Data;

@Data
public class RejectTaskRequest {
    private String targetNodeId;
    private String comment;
}
