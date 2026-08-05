package com.triobase.platform.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE 连接的认证后治理过滤器。
 *
 * <p>身份头必须由先执行的 JWT 过滤器覆盖写入；客户端查询参数不能指定租户或用户。配额租约在
 * 完成、取消、错误和最大生命周期超时时统一释放。日志只记录 TraceId，不记录 Token 或用户身份。</p>
 */
@Component
public class SseConnectionGovernanceFilter implements GlobalFilter, Ordered {

    static final String SSE_PATH = "/api/v2/inbox/events";
    private static final Logger log = LoggerFactory.getLogger(SseConnectionGovernanceFilter.class);

    private final int maxConnections;
    private final int maxConnectionsPerUser;
    private final Duration maxConnectionDuration;
    private final AtomicInteger total = new AtomicInteger();
    private final ConcurrentHashMap<String, AtomicInteger> scoped = new ConcurrentHashMap<>();

    public SseConnectionGovernanceFilter(
            @Value("${triobase.sse.max-connections:10000}") int maxConnections,
            @Value("${triobase.sse.max-connections-per-user:3}") int maxConnectionsPerUser,
            @Value("${triobase.sse.max-connection-duration:PT30M}") Duration maxConnectionDuration) {
        this.maxConnections = maxConnections;
        this.maxConnectionsPerUser = maxConnectionsPerUser;
        this.maxConnectionDuration = maxConnectionDuration;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!SSE_PATH.equals(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange);
        }
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (blank(tenantId) || blank(userId)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "identity_missing");
        }
        String key = tenantId + ':' + userId;
        AtomicInteger userCount = scoped.computeIfAbsent(key, ignored -> new AtomicInteger());
        if (total.incrementAndGet() > maxConnections) {
            total.decrementAndGet();
            removeIfEmpty(key, userCount);
            return reject(exchange, HttpStatus.TOO_MANY_REQUESTS, "global_quota");
        }
        if (userCount.incrementAndGet() > maxConnectionsPerUser) {
            userCount.decrementAndGet();
            total.decrementAndGet();
            removeIfEmpty(key, userCount);
            return reject(exchange, HttpStatus.TOO_MANY_REQUESTS, "user_quota");
        }

        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
        exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        exchange.getResponse().getHeaders().set("X-Accel-Buffering", "no");
        return chain.filter(exchange)
                .timeout(maxConnectionDuration)
                .onErrorResume(java.util.concurrent.TimeoutException.class,
                        timeout -> exchange.getResponse().setComplete())
                .doFinally(signal -> {
                    userCount.decrementAndGet();
                    total.decrementAndGet();
                    removeIfEmpty(key, userCount);
                });
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String reason) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        log.warn("SSE connection rejected reason={} traceId={}", reason,
                blank(traceId) ? "unknown" : traceId);
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    private void removeIfEmpty(String key, AtomicInteger counter) {
        if (counter.get() <= 0) {
            scoped.remove(key, counter);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
