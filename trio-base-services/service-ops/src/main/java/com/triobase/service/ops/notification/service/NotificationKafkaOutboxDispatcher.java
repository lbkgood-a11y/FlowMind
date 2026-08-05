package com.triobase.service.ops.notification.service;

import com.triobase.service.ops.notification.entity.NotificationOutboxEntity;
import com.triobase.service.ops.notification.mapper.NotificationOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 竞争发布通知 Outbox 并等待 Kafka broker 确认。
 *
 * <p>发布确认与数据库 acknowledge 之间仍可能崩溃，因此下游必须以 eventId/idempotencyKey
 * 去重。错误摘要不写入 Outbox，避免 broker 或凭据细节进入业务数据库。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "triobase.notification.kafka", name = "enabled", havingValue = "true")
public class NotificationKafkaOutboxDispatcher {

    private final NotificationOutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.application.name:service-ops}")
    private String workerName;
    @Value("${triobase.notification.kafka.accepted-topic:triobase.notification.accepted.v1}")
    private String acceptedTopic;

    @Scheduled(fixedDelayString = "${triobase.notification.kafka.dispatch-delay-ms:1000}")
    public void dispatch() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationOutboxEntity> ready = outboxMapper.findReady(now, now.minusMinutes(2), 100);
        for (NotificationOutboxEntity event : ready) {
            publishOne(event);
        }
    }

    private void publishOne(NotificationOutboxEntity event) {
        LocalDateTime now = LocalDateTime.now();
        if (outboxMapper.claim(event.getId(), workerName, now, now.minusMinutes(2)) != 1) {
            return;
        }
        try {
            kafkaTemplate.send(acceptedTopic, event.getEventId(), event.getPayload()).get(10, TimeUnit.SECONDS);
            outboxMapper.acknowledge(event.getId(), workerName, LocalDateTime.now());
        } catch (Exception deliveryFailure) {
            int attempts = Math.max(event.getAttemptCount() == null ? 0 : event.getAttemptCount(), 0) + 1;
            long delaySeconds = Math.min(300L, 1L << Math.min(attempts, 8));
            outboxMapper.releaseForRetry(event.getId(), workerName, LocalDateTime.now().plusSeconds(delaySeconds));
        }
    }
}
