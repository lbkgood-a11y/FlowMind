package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role_auth_compiled_evidence")
public class SysRoleAuthCompiledEvidence {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String releaseId;
    private String capabilityCode;
    private String projectionType;
    private String projectionKey;
    private String resourceCode;
    private String actionCode;
    private String effect;
    private String projectionSnapshot;
    private LocalDateTime createdAt;
}
