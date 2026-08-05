package com.triobase.service.ops.announcement.dto;

import java.time.LocalDateTime;

/** 受单独敏感读取权限保护的个人回执证据。 */
public record AnnouncementEvidenceRow(
        String recipientUserId,
        LocalDateTime readAt,
        LocalDateTime confirmedAt,
        boolean overdue) {
}
