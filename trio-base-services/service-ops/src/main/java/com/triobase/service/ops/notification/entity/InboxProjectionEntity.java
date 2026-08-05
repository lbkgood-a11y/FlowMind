package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_inbox_projection")
public class InboxProjectionEntity {
    @TableId
    private String id;
    private String tenantId;
    private String taskId;
    private String recipientUserId;
    private String channelCode;
    private String itemType;
    private String title;
    private String summary;
    private String sourceOwner;
    private String resourceType;
    private String resourceId;
    private String resourceKey;
    private String actionId;
    private LocalDateTime readAt;
    private LocalDateTime archivedAt;
    private LocalDateTime hiddenAt;
    private LocalDateTime withdrawnAt;
    private LocalDateTime receivedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
