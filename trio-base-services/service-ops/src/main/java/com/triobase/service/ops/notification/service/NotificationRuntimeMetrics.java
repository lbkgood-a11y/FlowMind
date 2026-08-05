package com.triobase.service.ops.notification.service;

import com.triobase.service.ops.announcement.mapper.AnnouncementReceiptMapper;
import com.triobase.service.ops.notification.mapper.NotificationTaskMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 通知运行时的低基数指标。
 *
 * <p>标签只使用固定阶段与结果，不包含租户、任务、收件人、模板或异常正文，避免指标系统成为
 * 敏感数据旁路。积压与队列时延从全局 Worker 队列汇总，租户明细仅在授权诊断接口查询。</p>
 */
@Component
public class NotificationRuntimeMetrics {

    private final Counter resolvedRecipients;
    private final Counter deliveredRecipients;
    private final Counter retryScheduled;
    private final Counter failures;
    private final Counter retentionFailures;
    private final Timer deliveryLatency;

    public NotificationRuntimeMetrics(MeterRegistry registry,
                                      NotificationTaskMapper taskMapper,
                                      AnnouncementReceiptMapper receiptMapper) {
        resolvedRecipients = registry.counter("triobase.notification.resolved.total");
        deliveredRecipients = registry.counter("triobase.notification.delivered.total");
        retryScheduled = registry.counter("triobase.notification.retry.total");
        failures = registry.counter("triobase.notification.failure.total");
        retentionFailures = registry.counter("triobase.notification.retention.failure.total");
        deliveryLatency = registry.timer("triobase.notification.delivery.latency");
        Gauge.builder("triobase.notification.backlog", taskMapper, NotificationTaskMapper::countBacklog)
                .register(registry);
        Gauge.builder("triobase.notification.queue.lag.seconds", taskMapper,
                        NotificationRuntimeMetrics::queueLagSeconds)
                .register(registry);
        Gauge.builder("triobase.notification.confirmation.overdue", receiptMapper,
                        AnnouncementReceiptMapper::countOverdueConfirmations)
                .register(registry);
    }

    public void resolved(long count) {
        resolvedRecipients.increment(count);
    }

    public void delivered(long count) {
        deliveredRecipients.increment(count);
    }

    public void failed(boolean retryable) {
        failures.increment();
        if (retryable) {
            retryScheduled.increment();
        }
    }

    public void completed(LocalDateTime createdAt) {
        if (createdAt != null) {
            deliveryLatency.record(Duration.between(
                    createdAt.toInstant(ZoneOffset.UTC), java.time.Instant.now()));
        }
    }

    public void retentionFailed() {
        retentionFailures.increment();
    }

    private static double queueLagSeconds(NotificationTaskMapper mapper) {
        LocalDateTime oldest = mapper.oldestBacklogCreatedAt();
        if (oldest == null) {
            return 0;
        }
        return Math.max(0, Duration.between(oldest, LocalDateTime.now()).toSeconds());
    }
}
