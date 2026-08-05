package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_notification_user_preference")
public class NotificationUserPreferenceEntity {
    private String id;
    private String tenantId;
    private String userId;
    private String categoryCode;
    private String channelCode;
    private Integer enabled;
    private String quietHoursJson;
    private LocalDateTime updatedAt;
}

