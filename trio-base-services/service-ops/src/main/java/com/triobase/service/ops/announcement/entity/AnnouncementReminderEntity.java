package com.triobase.service.ops.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_announcement_reminder")
public class AnnouncementReminderEntity {
    @TableId
    private String id;
    private String tenantId;
    private String versionId;
    private String recipientUserId;
    private String reminderKey;
    private String notificationTaskId;
    private String requestedBy;
    private String traceId;
    private LocalDateTime requestedAt;
}
