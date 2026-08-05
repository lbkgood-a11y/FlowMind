package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_notification_outbox")
public class NotificationOutboxEntity {
    @TableId
    private String id;
    private String tenantId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String eventId;
    private String payload;
    private String traceId;
    private LocalDateTime publishedAt;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lockedAt;
    private String lockedBy;
    private LocalDateTime createdAt;
}
