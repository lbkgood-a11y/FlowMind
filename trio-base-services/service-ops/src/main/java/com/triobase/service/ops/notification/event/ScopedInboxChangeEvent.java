package com.triobase.service.ops.notification.event;

import com.triobase.common.dto.notification.InboxSseEvent;

/** 服务端路由信封；对客户端序列化时只发送 payload，不暴露路由身份字段。 */
public record ScopedInboxChangeEvent(String tenantId, String userId, InboxSseEvent payload) {
}
