package com.triobase.common.dto.notification;

/**
 * 指向业务 Owner 所拥有事实的受治理引用。
 *
 * <p>{@code resourceKey} 和 {@code actionId} 必须来自平台注册表；契约刻意不提供 URL 字段，
 * 防止通知或 Agent 绕过 Owner 的实时授权、确认、幂等和审计入口。</p>
 */
public record BusinessResourceReference(
        String ownerService,
        String resourceType,
        String resourceId,
        String resourceKey,
        String actionId) {
}
