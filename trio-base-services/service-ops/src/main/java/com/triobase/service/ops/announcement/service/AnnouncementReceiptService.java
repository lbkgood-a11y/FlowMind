package com.triobase.service.ops.announcement.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.ops.announcement.domain.AnnouncementState;
import com.triobase.service.ops.announcement.entity.AnnouncementVersionEntity;
import com.triobase.service.ops.announcement.mapper.AnnouncementReceiptMapper;
import com.triobase.service.ops.announcement.mapper.AnnouncementVersionMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 记录公告阅读和明确确认，不把“打开页面”误当成确认。
 *
 * <p>确认必须提交当前发布版本的声明哈希，避免客户端对旧声明或被篡改声明产生回执。冻结受众
 * 还要求用户存在于发布快照；动态普通公告只允许阅读，不接受强制确认。</p>
 */
@Service
@RequiredArgsConstructor
public class AnnouncementReceiptService {

    private final AnnouncementVersionMapper versionMapper;
    private final AnnouncementReceiptMapper receiptMapper;
    private final AnnouncementVisibilityMapper visibilityMapper;
    private final RequestContextService contextService;

    @Transactional
    public void markRead(String versionId, Instant occurredAt,
                         java.util.List<String> orgIds, java.util.List<String> roleIds) {
        AnnouncementVersionEntity version = requireVisible(versionId, orgIds, roleIds);
        receiptMapper.markRead(UlidGenerator.nextUlid(), contextService.tenantId(), version.getId(),
                contextService.userId(), utc(occurredAt));
    }

    @Transactional
    public void confirm(String versionId, String statementHash, Instant occurredAt) {
        AnnouncementVersionEntity version = requireVisible(versionId, java.util.List.of(), java.util.List.of());
        if (!Short.valueOf((short) 1).equals(version.getConfirmationRequired())) {
            throw new BizException(45221, "ANNOUNCEMENT_CONFIRMATION_NOT_REQUIRED");
        }
        if (statementHash == null || !statementHash.equals(version.getConfirmationStatementHash())) {
            throw new BizException(45222, "ANNOUNCEMENT_CONFIRMATION_STATEMENT_MISMATCH");
        }
        if (!visibilityMapper.isSnapshotRecipient(
                contextService.tenantId(), versionId, contextService.userId())) {
            throw new BizException(45223, "ANNOUNCEMENT_CONFIRMATION_FORBIDDEN");
        }
        receiptMapper.confirm(UlidGenerator.nextUlid(), contextService.tenantId(), versionId,
                contextService.userId(), utc(occurredAt), statementHash, TraceUtil.getTraceId());
    }

    private AnnouncementVersionEntity requireVisible(String versionId,
                                                      java.util.List<String> orgIds,
                                                      java.util.List<String> roleIds) {
        AnnouncementVersionEntity version = versionMapper.selectById(versionId);
        if (version == null || !contextService.tenantId().equals(version.getTenantId())
                || AnnouncementState.PUBLISHED.name().equals(version.getLifecycleState()) == false
                || !visibilityMapper.isVisible(contextService.tenantId(), versionId, contextService.userId(),
                        orgIds == null ? java.util.List.of() : orgIds,
                        roleIds == null ? java.util.List.of() : roleIds)) {
            throw new BizException(45220, "ANNOUNCEMENT_NOT_VISIBLE");
        }
        return version;
    }

    private LocalDateTime utc(Instant instant) {
        if (instant == null) {
            throw new BizException(45224, "ANNOUNCEMENT_RECEIPT_TIME_REQUIRED");
        }
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
