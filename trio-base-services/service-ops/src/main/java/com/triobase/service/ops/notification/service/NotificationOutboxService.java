package com.triobase.service.ops.notification.service;

import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.dto.notification.NotificationRequest;
import com.triobase.service.ops.notification.entity.NotificationOutboxEntity;
import com.triobase.service.ops.notification.entity.NotificationTaskEntity;
import com.triobase.service.ops.notification.mapper.NotificationOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 在通知任务事务内追加 Kafka 发布事实；调用方不得直接向 Kafka 发送后再补写数据库。 */
@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    private final NotificationOutboxMapper outboxMapper;

    public void appendAccepted(NotificationTaskEntity task,
                               NotificationRequest request,
                               String serializedPayload) {
        NotificationOutboxEntity event = new NotificationOutboxEntity();
        event.setId(UlidGenerator.nextUlid());
        event.setTenantId(task.getTenantId());
        event.setAggregateType("NOTIFICATION_TASK");
        event.setAggregateId(task.getId());
        event.setEventType("NOTIFICATION_TASK_ACCEPTED");
        event.setEventId(request.getEventId());
        event.setPayload(serializedPayload);
        event.setTraceId(request.getTraceId());
        event.setAttemptCount(0);
        event.setCreatedAt(LocalDateTime.now());
        outboxMapper.insert(event);
    }
}
