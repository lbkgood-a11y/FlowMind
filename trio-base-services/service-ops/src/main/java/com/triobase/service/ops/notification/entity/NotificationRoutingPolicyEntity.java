package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_notification_routing_policy")
public class NotificationRoutingPolicyEntity {
    private String id;
    private String tenantId;
    private String categoryCode;
    private String priorityCode;
    private String orderedChannels;
    private Integer fallbackEnabled;
    private String quietHoursJson;
    private Integer mandatoryCategory;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

