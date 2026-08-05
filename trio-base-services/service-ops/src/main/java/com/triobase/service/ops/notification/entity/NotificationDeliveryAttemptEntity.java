package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_notification_delivery_attempt")
public class NotificationDeliveryAttemptEntity {
    @TableId
    private String id;
    private String tenantId;
    private String taskId;
    private String projectionId;
    private String recipientUserId;
    private String channelCode;
    private Integer attemptNo;
    private String deliveryStatus;
    private Short retryable;
    private String errorCategory;
    private String sanitizedMessage;
    private LocalDateTime occurredAt;
}
