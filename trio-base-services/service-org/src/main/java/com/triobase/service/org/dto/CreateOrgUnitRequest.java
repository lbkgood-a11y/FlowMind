package com.triobase.service.org.dto;

import lombok.Data;

@Data
public class CreateOrgUnitRequest {
    private String parentId;
    private String unitCode;
    private String unitName;
    private Integer sortOrder;
    private Boolean enabled;
    private String description;
}
