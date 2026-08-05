package com.triobase.service.ops.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.dto.notification.AudienceSelector;
import com.triobase.common.dto.notification.AuthorizedAudienceResolver;
import com.triobase.common.dto.notification.BusinessResourceReference;
import com.triobase.common.dto.notification.NotificationRequest;
import com.triobase.service.ops.announcement.service.AuthorizedAudienceResolverRegistry;
import com.triobase.service.ops.notification.entity.InboxProjectionEntity;
import com.triobase.service.ops.notification.entity.NotificationResolutionCheckpointEntity;
import com.triobase.service.ops.notification.entity.NotificationTaskEntity;
import com.triobase.service.ops.notification.entity.NotificationDeliveryAttemptEntity;
import com.triobase.service.ops.notification.mapper.InboxProjectionMapper;
import com.triobase.service.ops.notification.mapper.NotificationDeliveryAttemptMapper;
import com.triobase.service.ops.notification.mapper.NotificationResolutionCheckpointMapper;
import com.triobase.service.ops.notification.mapper.NotificationTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Temporal Activity 的通知持久化实现。
 *
 * <p>所有入口都从标准 JSON 中同时取得 tenantId 与 taskId，并使用租户前导条件访问数据。
 * 解析保持只读；投影唯一键、计数增量和游标检查点在同一事务提交，避免 Activity 完成确认丢失
 * 时出现跳批或重复计数。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationDeliveryActivityService {

    static final int RESOLUTION_BATCH_SIZE = 500;

    private final NotificationTaskMapper taskMapper;
    private final NotificationResolutionCheckpointMapper checkpointMapper;
    private final InboxProjectionMapper projectionMapper;
    private final NotificationDeliveryAttemptMapper attemptMapper;
    private final AuthorizedAudienceResolverRegistry resolverRegistry;
    private final ObjectMapper objectMapper;
    private final NotificationRuntimeMetrics metrics;
    private final InboxChangePublisher changePublisher;

    @Transactional
    public String startResolution(String commandJson) {
        ObjectNode command = object(commandJson);
        NotificationTaskEntity task = requireTask(command);
        if (task.getExpiresAt() != null && !task.getExpiresAt().isAfter(LocalDateTime.now())) {
            taskMapper.advanceState(task.getTenantId(), task.getId(), "EXPIRED");
            command.put("terminalState", "EXPIRED");
            return write(command);
        }
        if ("ACCEPTED".equals(task.getTaskState())) {
            taskMapper.advanceState(task.getTenantId(), task.getId(), "RESOLVING");
        } else if (!List.of("RESOLVING", "DELIVERING").contains(task.getTaskState())) {
            throw new BizException(45401, "NOTIFICATION_TASK_NOT_DELIVERABLE");
        }
        command.set("request", read(task.getRequestPayload()));
        return write(command);
    }

    public String resolveAudienceBatch(String batchCommandJson) {
        ObjectNode command = object(batchCommandJson);
        NotificationTaskEntity task = requireTask(command);
        NotificationRequest request = request(command);
        AuthorizedAudienceResolver resolver = resolverRegistry.require(resolverKey(request.getAudience()));
        String cursor = nullableText(command.path("cursor"));
        AuthorizedAudienceResolver.AudienceResolutionPage page = resolver.resolve(
                task.getTenantId(), task.getProducer(), request.getAudience(), cursor,
                RESOLUTION_BATCH_SIZE);
        LinkedHashSet<String> recipients = new LinkedHashSet<>();
        if (page != null && page.authorizedUserIds() != null) {
            page.authorizedUserIds().stream()
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(recipients::add);
        }
        metrics.resolved(recipients.size());
        String nextCursor = page == null ? null : page.nextCursor();
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode ids = result.putArray("recipientUserIds");
        recipients.forEach(ids::add);
        result.put("resolverKey", resolver.resolverKey());
        result.put("resolverVersion", resolver.resolverVersion());
        if (nextCursor == null || nextCursor.isBlank()) {
            result.putNull("nextCursor");
            result.put("complete", true);
        } else {
            result.put("nextCursor", nextCursor);
            result.put("complete", false);
        }
        return write(result);
    }

    @Transactional
    public String projectBatch(String projectionCommandJson) {
        ObjectNode command = object(projectionCommandJson);
        NotificationTaskEntity task = requireTask(command);
        NotificationRequest request = request(command);
        JsonNode checkpoint = command.path("checkpoint");
        LocalDateTime now = LocalDateTime.now();
        int inserted = 0;
        for (JsonNode recipient : command.path("recipientUserIds")) {
            String userId = recipient.asText();
            if (!userId.isBlank()) {
                int projectionInserted = projectionMapper.insertIgnore(projection(task, request, userId, now));
                inserted += projectionInserted;
                attemptMapper.insertIgnore(deliveredAttempt(task, userId, now));
                if (projectionInserted == 1) {
                    changePublisher.afterCommit(task.getTenantId(), userId, "INBOX_ITEM_CREATED");
                }
            }
        }
        checkpointMapper.upsert(checkpoint(task, checkpoint, inserted, now));
        taskMapper.addDelivered(task.getTenantId(), task.getId(), inserted);
        metrics.delivered(inserted);
        return result("DELIVERING", inserted);
    }

    @Transactional
    public String completeDelivery(String commandJson) {
        NotificationTaskEntity task = requireTask(object(commandJson));
        taskMapper.advanceState(task.getTenantId(), task.getId(), "DELIVERED");
        metrics.completed(task.getCreatedAt());
        return result("DELIVERED", 0);
    }

    @Transactional
    public String failDelivery(String commandJson) {
        ObjectNode command = object(commandJson);
        NotificationTaskEntity task = requireTask(command);
        boolean retryable = command.path("retryable").asBoolean(false);
        String state = task.getDeliveredCount() != null && task.getDeliveredCount() > 0
                ? "PARTIALLY_DELIVERED" : "FAILED";
        LocalDateTime now = LocalDateTime.now();
        taskMapper.recordFailure(task.getTenantId(), task.getId(), state,
                retryable ? now.plusMinutes(1) : null);
        NotificationDeliveryAttemptEntity attempt = new NotificationDeliveryAttemptEntity();
        attempt.setId(UlidGenerator.nextUlid());
        attempt.setTenantId(task.getTenantId());
        attempt.setTaskId(task.getId());
        attempt.setRecipientUserId("*");
        attempt.setChannelCode("IN_APP");
        attempt.setAttemptNo(Math.toIntExact(Math.min(
                (task.getFailedCount() == null ? 0 : task.getFailedCount()) + 1,
                Integer.MAX_VALUE)));
        attempt.setDeliveryStatus(state);
        attempt.setRetryable((short) (retryable ? 1 : 0));
        // Activity 入口可能被重放或由兼容 Worker 调用，持久化前必须再次清洗，不能信任字段名中的“sanitized”承诺。
        attempt.setErrorCategory(NotificationSafeText.classification(
                requiredText(command, "errorCategory")));
        attempt.setSanitizedMessage(NotificationSafeText.summary(
                requiredText(command, "sanitizedMessage")));
        attempt.setOccurredAt(now);
        attemptMapper.insertIgnore(attempt);
        metrics.failed(retryable);
        return result(state, 0);
    }

    @Transactional
    public String cancelDelivery(String commandJson) {
        ObjectNode command = object(commandJson);
        NotificationTaskEntity task = requireTask(command);
        taskMapper.cancelUndelivered(task.getTenantId(), task.getId(), requiredReason(command));
        return result("CANCELLED", 0);
    }

    @Transactional
    public String withdrawDelivery(String commandJson) {
        ObjectNode command = object(commandJson);
        NotificationTaskEntity task = requireTask(command);
        String reason = requiredReason(command);
        LocalDateTime now = LocalDateTime.now();
        List<String> foundRecipients = projectionMapper.findActiveRecipientIds(task.getTenantId(), task.getId());
        List<String> recipients = foundRecipients == null ? List.of() : foundRecipients;
        projectionMapper.withdrawTask(task.getTenantId(), task.getId(), now);
        taskMapper.recordWithdrawal(task.getTenantId(), task.getId(), reason);
        recipients.forEach(userId -> changePublisher.afterCommit(
                task.getTenantId(), userId, "INBOX_ITEM_WITHDRAWN"));
        return result("WITHDRAWN", 0);
    }

    private NotificationTaskEntity requireTask(ObjectNode command) {
        String tenantId = requiredText(command, "tenantId");
        String taskId = requiredText(command, "taskId");
        NotificationTaskEntity task = taskMapper.findOwned(tenantId, taskId);
        if (task == null) {
            throw new BizException(45400, "NOTIFICATION_TASK_NOT_FOUND");
        }
        return task;
    }

    private NotificationRequest request(ObjectNode command) {
        try {
            return objectMapper.treeToValue(command.path("request"), NotificationRequest.class);
        } catch (JsonProcessingException exception) {
            throw new BizException(45402, "NOTIFICATION_TASK_PAYLOAD_INVALID");
        }
    }

    private String resolverKey(AudienceSelector selector) {
        return switch (selector.getType()) {
            case ALL, USER -> "authorized-users";
            case ORGANIZATION -> "organization-members";
            case ROLE -> "role-members";
            case DYNAMIC_PARTICIPANT -> selector.getResolverKey();
        };
    }

    private InboxProjectionEntity projection(NotificationTaskEntity task, NotificationRequest request,
                                               String userId, LocalDateTime now) {
        InboxProjectionEntity entity = new InboxProjectionEntity();
        entity.setId(UlidGenerator.nextUlid());
        entity.setTenantId(task.getTenantId());
        entity.setTaskId(task.getId());
        entity.setRecipientUserId(userId);
        entity.setChannelCode("IN_APP");
        entity.setItemType("NOTIFICATION");
        // 模板渲染在渠道配置能力接入前保持最小化；禁止把任意变量拼接进预览正文。
        entity.setTitle(task.getTemplateKey());
        BusinessResourceReference reference = request.getResourceReference();
        if (reference != null) {
            entity.setSourceOwner(reference.ownerService());
            entity.setResourceType(reference.resourceType());
            entity.setResourceId(reference.resourceId());
            entity.setResourceKey(reference.resourceKey());
            entity.setActionId(reference.actionId());
        }
        entity.setReceivedAt(now);
        entity.setExpiresAt(request.getExpiresAt() == null ? null
                : LocalDateTime.ofInstant(request.getExpiresAt(), ZoneOffset.UTC));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private NotificationResolutionCheckpointEntity checkpoint(NotificationTaskEntity task,
                                                                JsonNode page, long inserted,
                                                                LocalDateTime now) {
        NotificationResolutionCheckpointEntity entity = new NotificationResolutionCheckpointEntity();
        entity.setId(UlidGenerator.nextUlid());
        entity.setTenantId(task.getTenantId());
        entity.setTaskId(task.getId());
        entity.setResolverKey(requiredText(page, "resolverKey"));
        entity.setResolverVersion(requiredText(page, "resolverVersion"));
        entity.setCursorValue(nullableText(page.path("nextCursor")));
        entity.setResolutionState(page.path("complete").asBoolean() ? "COMPLETED" : "RUNNING");
        entity.setResolvedCount(inserted);
        entity.setUpdatedAt(now);
        return entity;
    }

    private NotificationDeliveryAttemptEntity deliveredAttempt(NotificationTaskEntity task,
                                                                 String userId,
                                                                 LocalDateTime now) {
        NotificationDeliveryAttemptEntity attempt = new NotificationDeliveryAttemptEntity();
        attempt.setId(UlidGenerator.nextUlid());
        attempt.setTenantId(task.getTenantId());
        attempt.setTaskId(task.getId());
        attempt.setRecipientUserId(userId);
        attempt.setChannelCode("IN_APP");
        attempt.setAttemptNo(1);
        attempt.setDeliveryStatus("DELIVERED");
        attempt.setRetryable((short) 0);
        attempt.setOccurredAt(now);
        return attempt;
    }

    private String requiredReason(ObjectNode command) {
        String reason = requiredText(command, "reason");
        return reason.length() <= 512 ? reason : reason.substring(0, 512);
    }

    private String result(String state, long inserted) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("state", state);
        result.put("inserted", inserted);
        return write(result);
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) {
            throw new BizException(45403, "NOTIFICATION_ACTIVITY_COMMAND_INVALID:" + field);
        }
        return value;
    }

    private String nullableText(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private ObjectNode object(String json) {
        JsonNode node = read(json);
        if (!node.isObject()) {
            throw new BizException(45402, "NOTIFICATION_TASK_PAYLOAD_INVALID");
        }
        return (ObjectNode) node;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new BizException(45402, "NOTIFICATION_TASK_PAYLOAD_INVALID");
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new BizException(45402, "NOTIFICATION_TASK_PAYLOAD_INVALID");
        }
    }
}
