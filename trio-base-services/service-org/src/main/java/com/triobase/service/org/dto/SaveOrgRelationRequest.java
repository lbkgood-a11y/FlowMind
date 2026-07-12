package com.triobase.service.org.dto;

import lombok.Data;

@Data
public class SaveOrgRelationRequest {
    private String parentUnitId;
    private Integer sortOrder;
    private Boolean enabled;
}
