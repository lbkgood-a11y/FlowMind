package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class RuntimeWorkflowResponse {
    private String processInstanceId;
    private String processPackageId;
    private String processKey;
    private Integer version;
    private String status;
}
