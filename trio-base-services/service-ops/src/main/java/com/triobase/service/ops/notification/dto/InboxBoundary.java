package com.triobase.service.ops.notification.dto;

import java.time.LocalDateTime;

/** 服务端签发的“全部已读”高水位；同时间戳以不可变 ID 作为稳定次序。 */
public record InboxBoundary(LocalDateTime receivedAt, String id) {
}
