package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class ValidateRoleAuthorizationRequest {
    private String tenantId;
    private Long expectedVersion;
    private Boolean acknowledgePermissionExpansion;
}
