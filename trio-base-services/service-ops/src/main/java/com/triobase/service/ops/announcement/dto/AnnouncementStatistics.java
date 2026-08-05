package com.triobase.service.ops.announcement.dto;

import java.time.LocalDateTime;

/** 公告运营聚合统计；不包含个人身份或个人阅读时间。 */
public record AnnouncementStatistics(
        long accountableCount,
        long readCount,
        long confirmedCount,
        long overdueCount,
        LocalDateTime calculatedAt) {
}
