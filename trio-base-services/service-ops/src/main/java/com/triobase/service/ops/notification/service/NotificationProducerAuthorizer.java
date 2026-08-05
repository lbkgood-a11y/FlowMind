package com.triobase.service.ops.notification.service;

/**
 * 判断调用服务是否可代表指定租户创建通知任务。
 *
 * <p>实现必须依据受信服务身份和租户授权，而非请求正文中的 producer 字段。</p>
 */
public interface NotificationProducerAuthorizer {
    boolean isAuthorized(String callerService, String tenantId, String declaredProducer);
}
