package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_notification_template")
public class NotificationTemplateEntity {
    private String id;
    private String tenantId;
    private String templateKey;
    private String channelCode;
    private String localeCode;
    private String currentVersionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

