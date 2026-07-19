package com.triobase.service.auth.dto;

import com.triobase.service.auth.entity.SysAuthGrant;
import lombok.Data;

@Data
public class AuthorizationGrantResponse {
    private String id;
    private String tenantId;
    private String subjectType;
    private String subjectId;
    private String resourceCode;
    private String actionCode;
    private String effect;
    private Short status;
    private String description;

    public static AuthorizationGrantResponse from(SysAuthGrant grant) {
        AuthorizationGrantResponse response = new AuthorizationGrantResponse();
        response.setId(grant.getId());
        response.setTenantId(grant.getTenantId());
        response.setSubjectType(grant.getSubjectType());
        response.setSubjectId(grant.getSubjectId());
        response.setResourceCode(grant.getResourceCode());
        response.setActionCode(grant.getActionCode());
        response.setEffect(grant.getEffect());
        response.setStatus(grant.getStatus());
        response.setDescription(grant.getDescription());
        return response;
    }
}
