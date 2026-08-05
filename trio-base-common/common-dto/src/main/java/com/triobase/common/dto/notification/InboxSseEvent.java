package com.triobase.common.dto.notification;

import java.time.Instant;

/**
 * SSE 只用于通知客户端状态可能变化，不包含消息正文，也不是未读状态的事实来源。
 */
public record InboxSseEvent(String eventId, String kind, String changeHint, Instant occurredAt) {
}
