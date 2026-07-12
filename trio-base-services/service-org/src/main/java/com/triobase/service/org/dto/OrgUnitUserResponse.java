package com.triobase.service.org.dto;

import com.triobase.service.org.entity.SysOrgUnit;
import com.triobase.service.org.entity.SysUserOrgUnit;
import com.triobase.service.org.entity.SysUserView;
import lombok.Data;

import java.time.LocalDate;

@Data
public class OrgUnitUserResponse {
    private String assignmentId;
    private String userId;
    private String username;
    private String email;
    private String phone;
    private Integer userStatus;
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

    public static OrgUnitUserResponse from(
            SysUserOrgUnit relation,
            String dimensionCode,
            SysOrgUnit orgUnit,
            SysUserView user
    ) {
        OrgUnitUserResponse response = new OrgUnitUserResponse();
        response.setAssignmentId(relation.getId());
        response.setUserId(relation.getUserId());
        response.setUsername(user != null ? user.getUsername() : null);
        response.setEmail(user != null ? user.getEmail() : null);
        response.setPhone(user != null ? user.getPhone() : null);
        response.setUserStatus(user != null ? user.getStatus() : null);
        response.setDimensionId(relation.getDimensionId());
        response.setDimensionCode(dimensionCode);
        response.setOrgUnitId(relation.getOrgUnitId());
        response.setOrgUnitName(orgUnit != null ? orgUnit.getUnitName() : null);
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
