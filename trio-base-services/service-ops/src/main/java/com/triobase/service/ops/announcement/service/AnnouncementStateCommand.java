package com.triobase.service.ops.announcement.service;

import com.triobase.service.ops.announcement.domain.AnnouncementCommand;

import java.time.Instant;

/** 应用层公告状态命令；{@code occurredAt} 必须来自可信服务端时钟或调度触发时间。 */
public record AnnouncementStateCommand(
        AnnouncementCommand command,
        Instant occurredAt,
        Instant scheduledPublishAt,
        String reason) {
}
