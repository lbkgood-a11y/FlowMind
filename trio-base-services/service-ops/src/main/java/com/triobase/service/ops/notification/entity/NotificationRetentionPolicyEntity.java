package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 租户通知证据保留策略；天数均按 UTC 处理时间计算，批次上限由运行时再次收窄。 */
@Data
@TableName("ops_notification_retention_policy")
public class NotificationRetentionPolicyEntity {
    private String id;
    private String tenantId;
    private Integer projectionDays;
    private Integer receiptDays;
    private Integer deliveryDays;
    private Integer announcementDays;
    private Integer auditDays;
    private Integer purgeBatchSize;
    private Short enabled;
}

