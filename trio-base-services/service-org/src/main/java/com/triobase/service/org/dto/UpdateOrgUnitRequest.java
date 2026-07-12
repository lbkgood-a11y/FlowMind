package com.triobase.service.org.dto;

import lombok.Data;

@Data
public class UpdateOrgUnitRequest {
    private String unitName;
    private String unitType;
    private Integer sortOrder;
    private Boolean enabled;
    private String description;
}
