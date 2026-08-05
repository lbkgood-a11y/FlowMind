package com.triobase.service.ops.notification.dto;

import com.triobase.common.dto.notification.InboxItem;

import java.util.List;

public record InboxBellPreview(long unreadCount, List<InboxItem> recentItems,
                               InboxBoundary boundary) {
}
