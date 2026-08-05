package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_notification_channel")
public class NotificationChannelEntity {
    private String id;
    private String tenantId;
    private String channelCode;
    private String capabilityState;
    private Integer desiredEnabled;
    private String adapterKey;
    private String adapterVersion;
    private LocalDateTime validatedAt;
    private String validationSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

