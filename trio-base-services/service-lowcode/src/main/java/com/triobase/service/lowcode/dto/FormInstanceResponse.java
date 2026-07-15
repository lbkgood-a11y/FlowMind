package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FormInstanceResponse {
    private String id;
    private String formDefinitionId;
    private String formKey;
    private String status;
    private String dataJson;
    private String submittedBy;
    private String processKey;
    private String processInstanceId;
    private String workflowStatus;
    private LocalDateTime submittedAt;
}
