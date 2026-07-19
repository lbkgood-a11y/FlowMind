package com.triobase.service.auth.dto;

import com.triobase.service.auth.entity.SysAuthGuardTemplate;
import lombok.Data;

@Data
public class GuardTemplateResponse {
    private String id;
    private String tenantId;
    private String guardCode;
    private String ownerService;
    private String supportedResourceTypes;
    private String configSchemaJson;
    private String description;
    private Short status;

    public static GuardTemplateResponse from(SysAuthGuardTemplate template) {
        GuardTemplateResponse response = new GuardTemplateResponse();
        response.setId(template.getId());
        response.setTenantId(template.getTenantId());
        response.setGuardCode(template.getGuardCode());
        response.setOwnerService(template.getOwnerService());
        response.setSupportedResourceTypes(template.getSupportedResourceTypes());
        response.setConfigSchemaJson(template.getConfigSchemaJson());
        response.setDescription(template.getDescription());
        response.setStatus(template.getStatus());
        return response;
    }
}
