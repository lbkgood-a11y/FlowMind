package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_notification_resolution_checkpoint")
public class NotificationResolutionCheckpointEntity {
    @TableId
    private String id;
    private String tenantId;
    private String taskId;
    private String resolverKey;
    private String resolverVersion;
    private String cursorValue;
    private String resolutionState;
    private Long resolvedCount;
    private LocalDateTime updatedAt;
}
