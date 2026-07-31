package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role_auth_drift")
public class SysRoleAuthDrift {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String roleId;
    private String releaseId;
    private String capabilityCode;
    private Long oldCatalogVersion;
    private Long newCatalogVersion;
    private String driftType;
    private String driftStatus;
    private Long affectedUserCount;
    private String impactSummary;
    private LocalDateTime detectedAt;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionReleaseId;
}
