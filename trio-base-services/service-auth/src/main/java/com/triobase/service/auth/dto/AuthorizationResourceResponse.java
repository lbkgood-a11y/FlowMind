package com.triobase.service.auth.dto;

import com.triobase.service.auth.entity.SysAuthResource;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuthorizationResourceResponse {
    private String id;
    private String tenantId;
    private String resourceCode;
    private String resourceType;
    private String ownerService;
    private String businessObjectId;
    private String displayName;
    private String lifecycleStatus;
    private Boolean globalResource;
    private LocalDateTime lastSyncedAt;

    public static AuthorizationResourceResponse from(SysAuthResource resource) {
        AuthorizationResourceResponse response = new AuthorizationResourceResponse();
        response.setId(resource.getId());
        response.setTenantId(resource.getTenantId());
        response.setResourceCode(resource.getResourceCode());
        response.setResourceType(resource.getResourceType());
        response.setOwnerService(resource.getOwnerService());
        response.setBusinessObjectId(resource.getBusinessObjectId());
        response.setDisplayName(resource.getDisplayName());
        response.setLifecycleStatus(resource.getLifecycleStatus());
        response.setGlobalResource(resource.getGlobalFlag() != null && resource.getGlobalFlag() == 1);
        response.setLastSyncedAt(resource.getLastSyncedAt());
        return response;
    }
}
