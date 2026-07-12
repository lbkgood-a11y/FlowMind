package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class SaveDataPolicyRequest {
    private String roleId;
    private String resourceCode;
    private String actionCode;
    private String effect;
    private String combineMode;
    private Integer status;
    private String description;
    private List<DataPolicyDimensionRequest> dimensions;
}
