package com.triobase.service.auth.dto;

import com.triobase.service.auth.entity.SysDataPolicy;
import com.triobase.service.auth.entity.SysDataPolicyDimension;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DataPolicyResponse {
    private String id;
    private String roleId;
    private String resourceCode;
    private String actionCode;
    private String effect;
    private String combineMode;
    private Short status;
    private String description;
    private LocalDateTime createdAt;
    private List<DataPolicyDimensionResponse> dimensions;

    public static DataPolicyResponse from(SysDataPolicy policy, List<SysDataPolicyDimension> dimensions) {
        DataPolicyResponse response = new DataPolicyResponse();
        response.setId(policy.getId());
        response.setRoleId(policy.getSubjectId());
        response.setResourceCode(policy.getResourceCode());
        response.setActionCode(policy.getActionCode());
        response.setEffect(policy.getEffect());
        response.setCombineMode(policy.getCombineMode());
        response.setStatus(policy.getStatus());
        response.setDescription(policy.getDescription());
        response.setCreatedAt(policy.getCreatedAt());
        response.setDimensions(dimensions.stream().map(DataPolicyDimensionResponse::from).toList());
        return response;
    }
}
