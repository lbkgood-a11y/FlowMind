package com.triobase.service.auth.dto;

import com.triobase.service.auth.entity.SysRoleAuthDrift;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class RoleAuthorizationDriftResponse {
    String driftId;
    String roleId;
    String capabilityCode;
    String driftType;
    String status;
    Long affectedUserCount;
    String impactSummary;
    LocalDateTime detectedAt;

    public static RoleAuthorizationDriftResponse from(SysRoleAuthDrift drift) {
        return RoleAuthorizationDriftResponse.builder()
                .driftId(drift.getId())
                .roleId(drift.getRoleId())
                .capabilityCode(drift.getCapabilityCode())
                .driftType(drift.getDriftType())
                .status(drift.getDriftStatus())
                .affectedUserCount(drift.getAffectedUserCount())
                .impactSummary(drift.getImpactSummary())
                .detectedAt(drift.getDetectedAt())
                .build();
    }
}
