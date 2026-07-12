package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class DataPolicyDimensionRequest {
    private String dimensionCode;
    private String scopeType;
    private List<String> orgUnitIds;
    private Integer sortOrder;
}
