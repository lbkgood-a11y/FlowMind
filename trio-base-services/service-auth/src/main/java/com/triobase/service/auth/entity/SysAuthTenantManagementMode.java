package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_auth_tenant_management_mode")
public class SysAuthTenantManagementMode {
    @TableId
    private String tenantId;
    private String managementMode;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
