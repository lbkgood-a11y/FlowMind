package com.triobase.service.ops.notification.dto;

import java.time.LocalDateTime;

/** 所有过滤条件均可空；readState 仅接受 UNREAD 或 READ。 */
public record InboxQuery(String itemType, String readState, String sourceOwner,
                         LocalDateTime from, LocalDateTime to, int page, int size) {
}
