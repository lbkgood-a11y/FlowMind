package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class ApplicationActionResponse {
    private String id;
    private String actionCode;
    private String actionType;
    private String label;
    private String permissionCode;
    private String formDefinitionId;
    private String processKey;
    private String metadataJson;
    private String status;
    private Integer sortOrder;
}
