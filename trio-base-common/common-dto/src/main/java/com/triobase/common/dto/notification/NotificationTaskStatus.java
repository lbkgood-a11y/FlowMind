package com.triobase.common.dto.notification;

/** 通知任务持久化生命周期；业务结果不得从通知软失败状态反推或回滚。 */
public enum NotificationTaskStatus {
    ACCEPTED,
    RESOLVING,
    DELIVERING,
    PARTIALLY_DELIVERED,
    DELIVERED,
    FAILED,
    EXPIRED,
    CANCELLED
}
