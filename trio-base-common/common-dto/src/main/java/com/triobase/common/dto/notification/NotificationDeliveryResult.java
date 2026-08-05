package com.triobase.common.dto.notification;

import java.time.Instant;

/**
 * 单渠道投递结果。错误只携带已清洗的分类和摘要，不得包含凭据或原始敏感载荷。
 */
public record NotificationDeliveryResult(
        String taskId,
        String recipientUserId,
        ChannelIntent.Channel channel,
        DeliveryStatus status,
        int attempt,
        boolean retryable,
        String errorCategory,
        String sanitizedMessage,
        Instant occurredAt) {

    public enum DeliveryStatus {
        DELIVERED,
        FAILED,
        SKIPPED_UNAVAILABLE,
        EXPIRED,
        WITHDRAWN
    }
}
