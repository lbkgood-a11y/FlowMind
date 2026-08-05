package com.triobase.service.ops.notification.service;

import com.triobase.service.ops.notification.event.ScopedInboxChangeEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * service-ops 内的用户级 SSE 连接表。
 *
 * <p>连接键由认证上下文生成；业务事件仅发送 payload。心跳是连接活性信号，不表示消息状态，
 * 发送失败或超过最大生命周期时立即清理，客户端必须重连并查询权威消息接口。</p>
 */
@Component
public class InboxSseBroker {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Connection>> connections =
            new ConcurrentHashMap<>();
    private final Duration maxLifetime;

    public InboxSseBroker(@Value("${triobase.notification.sse.max-lifetime:PT30M}") Duration maxLifetime,
                          MeterRegistry registry) {
        this.maxLifetime = maxLifetime;
        // 连接数只能全局聚合，禁止用租户或用户作为标签，避免高基数和身份侧漏。
        Gauge.builder("triobase.notification.sse.connections", this, InboxSseBroker::connectionCount)
                .register(registry);
    }

    public SseEmitter connect(String tenantId, String userId) {
        SseEmitter emitter = new SseEmitter(maxLifetime.toMillis());
        String key = key(tenantId, userId);
        Connection connection = new Connection(emitter, Instant.now());
        connections.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(connection);
        Runnable cleanup = () -> remove(key, connection);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        send(key, connection, SseEmitter.event().comment("connected"));
        return emitter;
    }

    @EventListener
    public void onChange(ScopedInboxChangeEvent event) {
        String key = key(event.tenantId(), event.userId());
        var scopedConnections = connections.get(key);
        if (scopedConnections != null) {
            scopedConnections.forEach(connection -> send(key, connection,
                    SseEmitter.event().id(event.payload().eventId())
                            .name("inbox-change").data(event.payload())));
        }
    }

    @Scheduled(fixedDelayString = "${triobase.notification.sse.keep-alive-ms:15000}")
    public void keepAliveAndCleanup() {
        Instant expiry = Instant.now().minus(maxLifetime);
        connections.forEach((key, scopedConnections) -> scopedConnections.forEach(connection -> {
            if (connection.createdAt().isBefore(expiry)) {
                connection.emitter().complete();
                remove(key, connection);
            } else {
                send(key, connection, SseEmitter.event().comment("keep-alive"));
            }
        }));
    }

    private void send(String key, Connection connection, SseEmitter.SseEventBuilder event) {
        try {
            connection.emitter().send(event);
        } catch (IOException | IllegalStateException sendFailure) {
            connection.emitter().complete();
            remove(key, connection);
        }
    }

    private void remove(String key, Connection connection) {
        connections.computeIfPresent(key, (ignored, scopedConnections) -> {
            scopedConnections.remove(connection);
            return scopedConnections.isEmpty() ? null : scopedConnections;
        });
    }

    private String key(String tenantId, String userId) {
        return tenantId + ':' + userId;
    }

    int connectionCount() {
        return connections.values().stream().mapToInt(CopyOnWriteArrayList::size).sum();
    }

    private record Connection(SseEmitter emitter, Instant createdAt) {
    }
}
