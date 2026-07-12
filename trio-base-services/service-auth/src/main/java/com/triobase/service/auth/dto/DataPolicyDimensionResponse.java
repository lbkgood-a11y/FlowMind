package com.triobase.service.auth.dto;

import com.triobase.service.auth.entity.SysDataPolicyDimension;
import lombok.Data;

import java.util.Arrays;
import java.util.List;

@Data
public class DataPolicyDimensionResponse {
    private String id;
    private String dimensionCode;
    private String scopeType;
    private List<String> orgUnitIds;
    private Integer sortOrder;

    public static DataPolicyDimensionResponse from(SysDataPolicyDimension dimension) {
        DataPolicyDimensionResponse response = new DataPolicyDimensionResponse();
        response.setId(dimension.getId());
        response.setDimensionCode(dimension.getDimensionCode());
        response.setScopeType(dimension.getScopeType());
        response.setOrgUnitIds(splitOrgUnitIds(dimension.getOrgUnitIds()));
        response.setSortOrder(dimension.getSortOrder());
        return response;
    }

    private static List<String> splitOrgUnitIds(String orgUnitIds) {
        if (orgUnitIds == null || orgUnitIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(orgUnitIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
