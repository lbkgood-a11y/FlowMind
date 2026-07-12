package com.triobase.service.org.dto;

import lombok.Data;

@Data
public class CreateOrgUnitRequest {
    private String dimensionCode;
    private String parentId;
    private String unitCode;
    private String unitName;
    private String unitType;
    private Integer sortOrder;
    private Boolean enabled;
    private String description;
}
