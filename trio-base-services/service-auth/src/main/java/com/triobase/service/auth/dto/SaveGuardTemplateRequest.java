package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class SaveGuardTemplateRequest {
    private String tenantId;
    private String guardCode;
    private String ownerService;
    private String supportedResourceTypes;
    private String configSchemaJson;
    private String description;
    private Integer status;
}
