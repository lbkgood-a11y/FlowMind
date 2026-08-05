package com.triobase.service.ops.notification.dto;

import com.triobase.common.dto.notification.InboxItem;

import java.util.List;

public record InboxPage(List<InboxItem> items, int page, int size, boolean hasMore) {
}
