package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_notification_task")
public class NotificationTaskEntity extends BaseEntity {
    private String tenantId;
    private String producer;
    private String eventId;
    private String idempotencyKey;
    private String schemaVersion;
    private String templateKey;
    private String templateVersion;
    private String requestPayload;
    private String taskState;
    private String audienceMode;
    private Long resolvedCount;
    private Long deliveredCount;
    private Long failedCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime expiresAt;
    private String cancellationReason;
    private String traceId;
}
