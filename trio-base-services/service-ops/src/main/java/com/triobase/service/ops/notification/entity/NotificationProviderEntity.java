package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_notification_provider")
public class NotificationProviderEntity {
    private String id;
    private String tenantId;
    private String channelCode;
    private String providerKey;
    private String displayName;
    private String credentialReference;
    private String settingsJson;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

