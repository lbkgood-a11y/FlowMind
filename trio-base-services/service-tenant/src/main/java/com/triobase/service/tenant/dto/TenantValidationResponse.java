package com.triobase.service.tenant.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantValidationResponse {
    private String tenantId;
    private String tenantName;
    private String status;
    private boolean active;
    private String inactiveReason;
    private String isolationMode;
    private String planCode;
    private Integer maxUsers;
    private LocalDateTime expireAt;

    public static TenantValidationResponse missing(String tenantId) {
        TenantValidationResponse response = new TenantValidationResponse();
        response.setTenantId(tenantId);
        response.setStatus("MISSING");
        response.setActive(false);
        response.setInactiveReason("TENANT_NOT_FOUND");
        return response;
    }
}
