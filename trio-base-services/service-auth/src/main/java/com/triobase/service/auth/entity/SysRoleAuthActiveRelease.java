package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role_auth_active_release")
public class SysRoleAuthActiveRelease {
    private String tenantId;
    private String roleId;
    private String releaseId;
    private String activatedBy;
    private LocalDateTime activatedAt;
    private String activationType;
}
