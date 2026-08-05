package com.triobase.common.dto.notification;

import java.time.Instant;

/**
 * 个人消息中心的最小化读取投影。
 *
 * <p>该对象不承载待办事实；{@code taskRelated} 仅用于分类，当前任务状态必须回业务 Owner
 * 查询。已读也不代表业务任务完成。</p>
 */
public record InboxItem(
        String id,
        String itemType,
        String title,
        String summary,
        Instant receivedAt,
        Instant readAt,
        Instant archivedAt,
        boolean withdrawn,
        boolean taskRelated,
        BusinessResourceReference resourceReference) {
}
