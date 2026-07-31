package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_auth_draft")
public class SysRoleAuthDraft extends BaseEntity {
    private String tenantId;
    private String roleId;
    private String catalogId;
    private String basedReleaseId;
    private String draftStatus;
    private Long intentVersion;
    private String validationTokenHash;
    private String validationPlanHash;
    private String validatedBy;
    private Long validationAuthorityVersion;
    private Short migrationReviewRequired;
    private Short migrationExpansionDetected;
    private Short migrationExpansionAcknowledged;
    private LocalDateTime validatedAt;
    private LocalDateTime validationExpiresAt;
    private String validationSummary;
}
