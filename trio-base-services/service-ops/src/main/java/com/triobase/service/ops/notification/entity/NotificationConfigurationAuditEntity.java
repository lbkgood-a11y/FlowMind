package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_notification_config_audit")
public class NotificationConfigurationAuditEntity {
    private String id;
    private String tenantId;
    private String actorUserId;
    private String resourceType;
    private String resourceKey;
    private String actionCode;
    private String safeDetails;
    private String traceId;
    private LocalDateTime occurredAt;
}

