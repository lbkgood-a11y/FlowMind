package com.triobase.service.org.dto;

import com.triobase.service.org.entity.SysUserOrgUnit;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserOrgAssignmentResponse {
    private String id;
    private String userId;
    private String dimensionId;
    private String dimensionCode;
    private String orgUnitId;
    private String orgUnitName;
    private Boolean primary;
    private String positionId;
    private String positionName;
    private Boolean leader;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Short status;

    public static UserOrgAssignmentResponse from(SysUserOrgUnit relation, String dimensionCode, String orgUnitName) {
        UserOrgAssignmentResponse response = new UserOrgAssignmentResponse();
        response.setId(relation.getId());
        response.setUserId(relation.getUserId());
        response.setDimensionId(relation.getDimensionId());
        response.setDimensionCode(dimensionCode);
        response.setOrgUnitId(relation.getOrgUnitId());
        response.setOrgUnitName(orgUnitName);
        response.setPrimary(relation.getIsPrimary() != null && relation.getIsPrimary() == 1);
        response.setPositionId(relation.getPositionId());
        response.setPositionName(relation.getPositionName());
        response.setLeader(relation.getIsLeader() != null && relation.getIsLeader() == 1);
        response.setEffectiveFrom(relation.getEffectiveFrom());
        response.setEffectiveTo(relation.getEffectiveTo());
        response.setStatus(relation.getStatus());
        return response;
    }
}
