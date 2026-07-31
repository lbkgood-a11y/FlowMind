package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_authorization_outbox")
public class LcAuthorizationOutbox extends BaseEntity {
    private String tenantId;
    private String aggregateType;
    private String aggregateId;
    private Integer aggregateVersion;
    private String operation;
    private String snapshotHash;
    private String payloadJson;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lockedAt;
    private Long acknowledgedRevision;
    private LocalDateTime acknowledgedAt;
    private String lastError;
}
