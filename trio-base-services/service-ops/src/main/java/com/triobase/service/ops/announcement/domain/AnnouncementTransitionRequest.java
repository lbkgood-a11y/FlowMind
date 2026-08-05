package com.triobase.service.ops.announcement.domain;

import java.time.Instant;

/**
 * 公告状态推进所需的显式上下文。
 *
 * <p>状态机不读取系统时间或安全上下文，以便调度重放和单元测试保持确定。调用应用服务负责
 * 在进入状态机前完成权限判断，并把可信处理时间和操作者身份写入不可变审计记录。</p>
 */
public record AnnouncementTransitionRequest(
        AnnouncementState currentState,
        AnnouncementCommand command,
        Instant occurredAt,
        Instant scheduledPublishAt,
        String reason,
        boolean separateReviewer,
        boolean emergencyAuthorized) {
}
