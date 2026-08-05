package com.triobase.service.ops.notification.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.notification.BusinessResourceReference;
import com.triobase.common.dto.notification.InboxItem;
import com.triobase.service.ops.notification.dto.InboxBellPreview;
import com.triobase.service.ops.notification.dto.InboxBoundary;
import com.triobase.service.ops.notification.entity.InboxProjectionEntity;
import com.triobase.service.ops.notification.mapper.InboxProjectionMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 当前用户的站内投影状态边界。
 *
 * <p>租户和用户只取自认证上下文，禁止由请求覆盖。读取、归档与隐藏互不删除共享投递证据；
 * 高水位更新采用 receivedAt/id 稳定顺序，事务开始后到达的新消息不会被误标已读。</p>
 */
@Service
@RequiredArgsConstructor
public class PersonalInboxService {

    private static final String WITHDRAWN_TITLE = "已撤回消息";
    private static final String WITHDRAWN_SUMMARY = "原内容已撤回，历史证据仍按策略保留。";

    private final InboxProjectionMapper mapper;
    private final RequestContextService contextService;
    private final InboxChangePublisher changePublisher;

    public InboxBellPreview bell(int requestedLimit) {
        String tenantId = contextService.tenantId();
        String userId = contextService.userId();
        int limit = Math.min(Math.max(requestedLimit, 1), 20);
        List<InboxProjectionEntity> entities = mapper.findRecent(tenantId, userId, limit);
        InboxBoundary boundary = entities.isEmpty() ? null
                : new InboxBoundary(entities.getFirst().getReceivedAt(), entities.getFirst().getId());
        return new InboxBellPreview(mapper.unreadCount(tenantId, userId),
                entities.stream().map(this::item).toList(), boundary);
    }

    @Transactional
    public void markRead(List<String> ids) {
        List<String> safeIds = ids == null ? List.of()
                : ids.stream().filter(id -> id != null && !id.isBlank()).distinct().limit(200).toList();
        if (!safeIds.isEmpty()) {
            String tenantId = contextService.tenantId();
            String userId = contextService.userId();
            mapper.markReadBatch(tenantId, userId, safeIds, LocalDateTime.now());
            changePublisher.afterCommit(tenantId, userId, "INBOX_READ_CHANGED");
        }
    }

    @Transactional
    public void markAllRead(InboxBoundary boundary) {
        if (boundary == null || boundary.receivedAt() == null
                || boundary.id() == null || boundary.id().isBlank()) {
            throw new BizException(45500, "INBOX_BOUNDARY_REQUIRED");
        }
        String tenantId = contextService.tenantId();
        String userId = contextService.userId();
        mapper.markAllRead(tenantId, userId, boundary.receivedAt(), boundary.id(), LocalDateTime.now());
        changePublisher.afterCommit(tenantId, userId, "INBOX_READ_CHANGED");
    }

    @Transactional
    public void archive(String id) {
        mutate(id, Mutation.ARCHIVE);
    }

    @Transactional
    public void restore(String id) {
        mutate(id, Mutation.RESTORE);
    }

    @Transactional
    public void hide(String id) {
        mutate(id, Mutation.HIDE);
    }

    private void mutate(String id, Mutation mutation) {
        String tenantId = contextService.tenantId();
        String userId = contextService.userId();
        if (mapper.findOwned(tenantId, userId, id) == null) {
            throw new BizException(45501, "INBOX_ITEM_NOT_FOUND");
        }
        LocalDateTime now = LocalDateTime.now();
        switch (mutation) {
            case ARCHIVE -> mapper.archive(tenantId, userId, id, now);
            case RESTORE -> mapper.restore(tenantId, userId, id, now);
            case HIDE -> mapper.hide(tenantId, userId, id, now);
        }
        changePublisher.afterCommit(tenantId, userId, "INBOX_VISIBILITY_CHANGED");
    }

    private InboxItem item(InboxProjectionEntity entity) {
        boolean withdrawn = entity.getWithdrawnAt() != null;
        BusinessResourceReference reference = entity.getResourceKey() == null ? null
                : new BusinessResourceReference(entity.getSourceOwner(), entity.getResourceType(),
                        entity.getResourceId(), entity.getResourceKey(), entity.getActionId());
        // 撤回后仅保留状态与证据引用；原始展示内容不得越过普通收件人 API 边界。
        return new InboxItem(entity.getId(), entity.getItemType(),
                withdrawn ? WITHDRAWN_TITLE : entity.getTitle(),
                withdrawn ? WITHDRAWN_SUMMARY : entity.getSummary(),
                instant(entity.getReceivedAt()), instant(entity.getReadAt()), instant(entity.getArchivedAt()),
                withdrawn, entity.getActionId() != null, reference);
    }

    private java.time.Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private enum Mutation { ARCHIVE, RESTORE, HIDE }
}
