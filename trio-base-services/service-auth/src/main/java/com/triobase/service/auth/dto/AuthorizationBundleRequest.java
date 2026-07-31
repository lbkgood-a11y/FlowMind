package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class AuthorizationBundleRequest {
    private String tenantId;
    private String roleId;
    private String applicationResourceCode;
    private String preset;
    private String idempotencyKey;
}

