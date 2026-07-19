package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class SaveAuthorizationGrantRequest {
    private String tenantId;
    private String subjectType;
    private String subjectId;
    private String resourceCode;
    private String actionCode;
    private String effect;
    private Integer status;
    private String description;
}
