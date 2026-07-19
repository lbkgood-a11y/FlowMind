package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class SaveFieldPolicyRequest {
    private String tenantId;
    private String subjectType;
    private String subjectId;
    private String resourceCode;
    private String fieldKey;
    private String readMode;
    private String writeMode;
    private String maskStrategy;
    private String effect;
    private Integer status;
    private String description;
}
