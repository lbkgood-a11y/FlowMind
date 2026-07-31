package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_sync_receipt")
public class SysAuthSyncReceipt extends BaseEntity {
    private String tenantId;
    private String ownerService;
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private Integer aggregateVersion;
    private String operation;
    private String snapshotHash;
    private Long resourceVersion;
    private String resourceCodesJson;
    private LocalDateTime acknowledgedAt;
}
