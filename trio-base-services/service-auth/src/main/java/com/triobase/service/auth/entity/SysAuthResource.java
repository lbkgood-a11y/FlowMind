package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_resource")
public class SysAuthResource extends BaseEntity {
    private String tenantId;
    private String resourceCode;
    private String resourceType;
    private String ownerService;
    private String businessObjectId;
    private String displayName;
    private String lifecycleStatus;
    private Short globalFlag;
    private String metadataJson;
    private LocalDateTime lastSyncedAt;
}
