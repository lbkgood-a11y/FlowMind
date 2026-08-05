package com.triobase.service.ops.notification.dto;

/** 单租户一次保留批次的安全聚合结果，不包含被清理主体或内容。 */
public record NotificationRetentionResult(String tenantId, int projections, int receipts,
                                          int deliveries, int announcements, int audits) {
}

