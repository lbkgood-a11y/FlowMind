package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class PublishRoleAuthorizationRequest {
    private String tenantId;
    private Long expectedVersion;
    private String validationToken;
}
