package com.triobase.service.ops.notification.service;

import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.dto.notification.InboxSseEvent;
import com.triobase.service.ops.notification.event.ScopedInboxChangeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * 提交后发布用户级轻量失效事件。
 *
 * <p>事务回滚不会产生事件；事件仅提示客户端刷新权威查询，不承载消息正文、未读真值或业务
 * 敏感字段。无事务调用表示其数据库操作已经以自动提交完成，可立即发布。</p>
 */
@Component
@RequiredArgsConstructor
public class InboxChangePublisher {

    public static final String REFRESH_HINT = "REFRESH_UNREAD_AND_RECENT";
    private final ApplicationEventPublisher applicationEventPublisher;

    public void afterCommit(String tenantId, String userId, String kind) {
        Runnable publish = () -> applicationEventPublisher.publishEvent(new ScopedInboxChangeEvent(
                tenantId, userId, new InboxSseEvent(
                        UlidGenerator.nextUlid(), kind, REFRESH_HINT, Instant.now())));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
