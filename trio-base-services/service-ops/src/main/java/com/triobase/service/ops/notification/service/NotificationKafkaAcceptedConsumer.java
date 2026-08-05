package com.triobase.service.ops.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.notification.NotificationRequest;
import com.triobase.service.ops.notification.entity.NotificationTaskEntity;
import com.triobase.service.ops.notification.mapper.NotificationTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 消费已持久化的通知事实并幂等启动 Temporal；消息正文只用于定位任务，不作为新的写入来源。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "triobase.notification.kafka.enabled", havingValue = "true")
public class NotificationKafkaAcceptedConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationTaskMapper taskMapper;
    private final NotificationWorkflowLauncher workflowLauncher;

    @KafkaListener(topics = "${triobase.notification.kafka.event-topic:notification-events}",
            groupId = "${spring.application.name}-notification-delivery")
    public void consume(String payload) {
        NotificationRequest request = read(payload);
        NotificationTaskEntity task = taskMapper.findByIdempotency(
                request.getTenantId(), request.getProducer(), request.getIdempotencyKey());
        if (task == null) {
            throw new BizException(45400, "NOTIFICATION_TASK_NOT_FOUND");
        }
        workflowLauncher.launch(task);
    }

    private NotificationRequest read(String payload) {
        try {
            return objectMapper.readValue(payload, NotificationRequest.class);
        } catch (JsonProcessingException exception) {
            throw new BizException(45402, "NOTIFICATION_TASK_PAYLOAD_INVALID");
        }
    }
}
