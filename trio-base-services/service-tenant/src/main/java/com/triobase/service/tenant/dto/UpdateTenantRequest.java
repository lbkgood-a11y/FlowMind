package com.triobase.service.tenant.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateTenantRequest {
    private String tenantName;
    private String shortName;
    private String tenantType;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String region;
    private String timezone;
    private String locale;
    private String industry;
    private String planCode;
    private Integer maxUsers;
    private LocalDateTime expireAt;
    private String attributesJson;
}
