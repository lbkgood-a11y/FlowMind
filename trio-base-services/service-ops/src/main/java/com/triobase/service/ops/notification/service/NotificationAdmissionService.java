package com.triobase.service.ops.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.dto.notification.NotificationContractValidator;
import com.triobase.common.dto.notification.NotificationRequest;
import com.triobase.common.dto.notification.NotificationTaskStatus;
import com.triobase.service.ops.notification.entity.NotificationTaskEntity;
import com.triobase.service.ops.notification.mapper.NotificationTaskMapper;
import com.triobase.service.ops.announcement.service.AuthorizedAudienceResolverRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 通知请求的持久化准入边界。
 *
 * <p>生产者鉴权使用传输层确认的 callerService；请求中的 producer 仅是契约声明。唯一约束是
 * 并发幂等性的最终仲裁者，重复插入必须返回原任务而不能向调用方伪报失败。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationAdmissionService {

    private final NotificationTaskMapper taskMapper;
    private final NotificationProducerAuthorizer producerAuthorizer;
    private final ObjectMapper objectMapper;
    private final AuthorizedAudienceResolverRegistry audienceResolverRegistry;
    private final NotificationOutboxService outboxService;

    @Transactional
    public NotificationTaskEntity admit(String callerService, NotificationRequest request) {
        if (request == null || !producerAuthorizer.isAuthorized(
                callerService, request.getTenantId(), request.getProducer())) {
            throw new BizException(45301, "NOTIFICATION_PRODUCER_FORBIDDEN");
        }
        List<String> errors = NotificationContractValidator.validate(
                request, audienceResolverRegistry.registeredKeys());
        if (!errors.isEmpty()) {
            throw new BizException(45302, "NOTIFICATION_CONTRACT_INVALID:" + errors.getFirst());
        }
        NotificationTaskEntity existing = findExisting(request);
        if (existing != null) {
            return existing;
        }
        NotificationTaskEntity task = toEntity(request);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException concurrentDuplicate) {
            NotificationTaskEntity duplicate = findExisting(request);
            if (duplicate == null) {
                throw concurrentDuplicate;
            }
            return duplicate;
        }
        outboxService.appendAccepted(task, request, task.getRequestPayload());
        return task;
    }

    private NotificationTaskEntity findExisting(NotificationRequest request) {
        return taskMapper.selectOne(new QueryWrapper<NotificationTaskEntity>()
                .eq("tenant_id", request.getTenantId())
                .eq("producer", request.getProducer())
                .eq("idempotency_key", request.getIdempotencyKey()));
    }

    private NotificationTaskEntity toEntity(NotificationRequest request) {
        NotificationTaskEntity task = new NotificationTaskEntity();
        task.setId(UlidGenerator.nextUlid());
        task.setTenantId(request.getTenantId());
        task.setProducer(request.getProducer());
        task.setEventId(request.getEventId());
        task.setIdempotencyKey(request.getIdempotencyKey());
        task.setSchemaVersion(request.getSchemaVersion());
        task.setTemplateKey(request.getTemplateKey());
        task.setTemplateVersion(request.getTemplateVersion());
        task.setRequestPayload(writePayload(request));
        task.setTaskState(NotificationTaskStatus.ACCEPTED.name());
        task.setAudienceMode(request.getAudience().isFreezeRequired() ? "FROZEN" : "DYNAMIC");
        task.setResolvedCount(0L);
        task.setDeliveredCount(0L);
        task.setFailedCount(0L);
        task.setExpiresAt(request.getExpiresAt() == null ? null
                : LocalDateTime.ofInstant(request.getExpiresAt(), ZoneOffset.UTC));
        task.setTraceId(request.getTraceId());
        return task;
    }

    private String writePayload(NotificationRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new BizException(45303, "NOTIFICATION_PAYLOAD_SERIALIZATION_FAILED");
        }
    }
}
