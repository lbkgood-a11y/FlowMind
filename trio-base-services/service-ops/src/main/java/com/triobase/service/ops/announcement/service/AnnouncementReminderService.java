package com.triobase.service.ops.announcement.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.common.dto.notification.AudienceSelector;
import com.triobase.common.dto.notification.BusinessResourceReference;
import com.triobase.common.dto.notification.ChannelIntent;
import com.triobase.common.dto.notification.NotificationRequest;
import com.triobase.service.ops.announcement.entity.AnnouncementReminderEntity;
import com.triobase.service.ops.announcement.entity.AnnouncementVersionEntity;
import com.triobase.service.ops.announcement.mapper.AnnouncementReminderMapper;
import com.triobase.service.ops.announcement.mapper.AnnouncementVersionMapper;
import com.triobase.service.ops.notification.entity.NotificationTaskEntity;
import com.triobase.service.ops.notification.service.NotificationAdmissionService;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 为冻结公告的未读或未确认用户创建幂等站内催读。
 *
 * <p>reminderKey 代表一次人工或定时催读批次；同一批次对同一用户最多产生一个通知任务。
 * 通知失败保持软失败，不修改公告发布或确认状态。</p>
 */
@Service
@RequiredArgsConstructor
public class AnnouncementReminderService {

    private static final String PRODUCER = "service-ops";

    private final AnnouncementVersionMapper versionMapper;
    private final AnnouncementReminderMapper reminderMapper;
    private final NotificationAdmissionService admissionService;
    private final RequestContextService contextService;

    @Transactional
    public ReminderBatchResult remind(String versionId,
                                      String reminderKey,
                                      ReminderMode mode,
                                      String afterUserId,
                                      int requestedLimit,
                                      Instant occurredAt) {
        AnnouncementVersionEntity version = requireFrozenVersion(versionId);
        if (reminderKey == null || reminderKey.isBlank() || mode == null) {
            throw new BizException(45240, "ANNOUNCEMENT_REMINDER_CONTEXT_REQUIRED");
        }
        int limit = Math.min(Math.max(requestedLimit, 1), 500);
        List<String> recipients = reminderMapper.eligibleRecipients(
                contextService.tenantId(), versionId, mode.name(),
                afterUserId == null ? "" : afterUserId, limit);
        int created = 0;
        for (String userId : recipients) {
            AnnouncementReminderEntity evidence = evidence(
                    versionId, userId, reminderKey, occurredAt);
            if (reminderMapper.insertIgnore(evidence) == 0) {
                continue;
            }
            NotificationTaskEntity task = admissionService.admit(PRODUCER,
                    notification(version, userId, reminderKey, mode, occurredAt));
            reminderMapper.bindTask(contextService.tenantId(), reminderKey, userId, task.getId());
            created++;
        }
        String next = recipients.size() == limit ? recipients.getLast() : null;
        return new ReminderBatchResult(created, next, next == null);
    }

    private AnnouncementVersionEntity requireFrozenVersion(String versionId) {
        AnnouncementVersionEntity version = versionMapper.selectById(versionId);
        if (version == null || !contextService.tenantId().equals(version.getTenantId())
                || !"FROZEN".equals(version.getAudienceMode())) {
            throw new BizException(45241, "ANNOUNCEMENT_REMINDER_NOT_AVAILABLE");
        }
        return version;
    }

    private AnnouncementReminderEntity evidence(String versionId, String userId,
                                                String reminderKey, Instant occurredAt) {
        AnnouncementReminderEntity entity = new AnnouncementReminderEntity();
        entity.setId(UlidGenerator.nextUlid());
        entity.setTenantId(contextService.tenantId());
        entity.setVersionId(versionId);
        entity.setRecipientUserId(userId);
        entity.setReminderKey(reminderKey);
        entity.setRequestedBy(contextService.userId());
        entity.setTraceId(TraceUtil.getTraceId());
        entity.setRequestedAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
        return entity;
    }

    private NotificationRequest notification(AnnouncementVersionEntity version,
                                             String userId,
                                             String reminderKey,
                                             ReminderMode mode,
                                             Instant occurredAt) {
        AudienceSelector audience = new AudienceSelector();
        audience.setType(AudienceSelector.AudienceType.USER);
        audience.setScopeTenantId(contextService.tenantId());
        audience.setSubjectIds(List.of(userId));
        audience.setFreezeRequired(true);

        NotificationRequest request = new NotificationRequest();
        request.setTenantId(contextService.tenantId());
        request.setProducer(PRODUCER);
        request.setEventId(reminderKey + ":" + userId);
        request.setIdempotencyKey("announcement-reminder:" + reminderKey + ":" + userId);
        request.setTemplateKey("announcement-reminder");
        request.setDeclaredVariables(Set.of("announcementTitle", "reminderMode"));
        request.setVariables(Map.of(
                "announcementTitle", version.getTitle(),
                "reminderMode", mode.name()));
        request.setAudience(audience);
        request.setChannelIntent(new ChannelIntent(List.of(ChannelIntent.Channel.IN_APP), false));
        request.setResourceReference(new BusinessResourceReference(
                PRODUCER, "ANNOUNCEMENT", version.getAnnouncementId(),
                "OPS.ANNOUNCEMENT.DETAIL", null));
        request.setTraceId(TraceUtil.getTraceId());
        return request;
    }

    public enum ReminderMode {
        UNREAD,
        UNCONFIRMED
    }

    public record ReminderBatchResult(int createdCount, String nextUserId, boolean completed) {
    }
}
