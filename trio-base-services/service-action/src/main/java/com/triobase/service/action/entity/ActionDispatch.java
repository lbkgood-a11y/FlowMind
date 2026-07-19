package com.triobase.service.action.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("act_action_dispatch")
public class ActionDispatch extends BaseEntity {
    private String actionId;
    private String tenantId;
    private String ownerService;
    private String ownerEndpoint;
    private String dispatchStatus;
    private Integer attemptCount;
    private Integer maxAttempts;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime completedAt;
}
