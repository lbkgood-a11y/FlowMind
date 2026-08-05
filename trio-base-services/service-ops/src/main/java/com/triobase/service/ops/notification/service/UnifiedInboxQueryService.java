package com.triobase.service.ops.notification.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.notification.BusinessResourceReference;
import com.triobase.common.dto.notification.InboxItem;
import com.triobase.service.ops.notification.dto.InboxPage;
import com.triobase.service.ops.notification.dto.InboxQuery;
import com.triobase.service.ops.notification.dto.UnifiedInboxRow;
import com.triobase.service.ops.notification.mapper.UnifiedInboxMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * 统一消息查询边界。
 *
 * <p>公告和个人投影在数据库中集合合并并稳定排序；Owner 成员上下文只用于收窄公告可见性。
 * 页面额外读取一条计算 hasMore，避免无界计数扫描。</p>
 */
@Service
@RequiredArgsConstructor
public class UnifiedInboxQueryService {

    private static final Set<String> READ_STATES = Set.of("UNREAD", "READ");
    private static final String WITHDRAWN_TITLE = "已撤回消息";
    private static final String WITHDRAWN_SUMMARY = "原内容已撤回，历史证据仍按策略保留。";
    private final UnifiedInboxMapper mapper;
    private final InboxMembershipContextService membershipContextService;
    private final RequestContextService contextService;

    public InboxPage find(InboxQuery query) {
        int page = Math.max(query.page(), 1);
        int size = Math.min(Math.max(query.size(), 1), 100);
        String readState = normalize(query.readState());
        if (readState != null && !READ_STATES.contains(readState)) {
            throw new BizException(45502, "INBOX_READ_STATE_INVALID");
        }
        String tenantId = contextService.tenantId();
        String userId = contextService.userId();
        var membership = membershipContextService.resolve(tenantId, userId);
        List<UnifiedInboxRow> rows = mapper.find(tenantId, userId,
                membership.organizationIds(), membership.roleIds(), LocalDateTime.now(),
                normalize(query.itemType()), readState, normalize(query.sourceOwner()),
                query.from(), query.to(), size + 1, (long) (page - 1) * size);
        boolean hasMore = rows.size() > size;
        List<InboxItem> items = rows.stream().limit(size).map(this::item).toList();
        return new InboxPage(items, page, size, hasMore);
    }

    private InboxItem item(UnifiedInboxRow row) {
        boolean withdrawn = Boolean.TRUE.equals(row.getWithdrawn());
        BusinessResourceReference reference = row.getResourceKey() == null ? null
                : new BusinessResourceReference(row.getSourceOwner(), row.getResourceType(),
                        row.getResourceId(), row.getResourceKey(), row.getActionId());
        // 数据层保留撤回证据，查询契约只向普通用户暴露固定安全标记。
        return new InboxItem(row.getId(), row.getItemType(),
                withdrawn ? WITHDRAWN_TITLE : row.getTitle(),
                withdrawn ? WITHDRAWN_SUMMARY : row.getSummary(),
                instant(row.getReceivedAt()), instant(row.getReadAt()), instant(row.getArchivedAt()),
                withdrawn, Boolean.TRUE.equals(row.getTaskRelated()), reference);
    }

    private java.time.Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
