package com.triobase.service.tenant.dto;

import com.triobase.service.tenant.entity.SysTenant;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantResponse {
    private String tenantId;
    private String tenantCode;
    private String tenantName;
    private String shortName;
    private String tenantType;
    private String status;
    private String isolationMode;
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
    private String suspendedReason;
    private String attributesJson;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public static TenantResponse from(SysTenant tenant) {
        TenantResponse response = new TenantResponse();
        response.setTenantId(tenant.getId());
        response.setTenantCode(tenant.getTenantCode());
        response.setTenantName(tenant.getTenantName());
        response.setShortName(tenant.getShortName());
        response.setTenantType(tenant.getTenantType());
        response.setStatus(tenant.getStatus());
        response.setIsolationMode(tenant.getIsolationMode());
        response.setContactName(tenant.getContactName());
        response.setContactEmail(tenant.getContactEmail());
        response.setContactPhone(tenant.getContactPhone());
        response.setRegion(tenant.getRegion());
        response.setTimezone(tenant.getTimezone());
        response.setLocale(tenant.getLocale());
        response.setIndustry(tenant.getIndustry());
        response.setPlanCode(tenant.getPlanCode());
        response.setMaxUsers(tenant.getMaxUsers());
        response.setExpireAt(tenant.getExpireAt());
        response.setSuspendedReason(tenant.getSuspendedReason());
        response.setAttributesJson(tenant.getAttributesJson());
        response.setCreatedBy(tenant.getCreatedBy());
        response.setCreatedAt(tenant.getCreatedAt());
        response.setUpdatedBy(tenant.getUpdatedBy());
        response.setUpdatedAt(tenant.getUpdatedAt());
        return response;
    }
}
