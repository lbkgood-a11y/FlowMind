package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role_auth_audit")
public class SysRoleAuthAudit {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String roleId;
    private String draftId;
    private String releaseId;
    private String eventType;
    private String actorId;
    private String businessSummary;
    private String technicalEvidence;
    private Long affectedUserCount;
    private String traceId;
    private LocalDateTime occurredAt;
}
