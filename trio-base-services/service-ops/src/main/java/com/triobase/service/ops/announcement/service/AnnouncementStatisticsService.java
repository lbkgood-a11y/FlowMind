package com.triobase.service.ops.announcement.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.ops.announcement.dto.AnnouncementEvidenceRow;
import com.triobase.service.ops.announcement.dto.AnnouncementStatistics;
import com.triobase.service.ops.announcement.mapper.AnnouncementReceiptMapper;
import com.triobase.service.ops.service.RequestContextService;
import com.triobase.service.ops.notification.service.NotificationSecurityAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 聚合统计默认开放给公告运营；个人证据需要独立敏感读取权限。 */
@Service
@RequiredArgsConstructor
public class AnnouncementStatisticsService {

    public static final String EVIDENCE_PERMISSION = "OPS.ANNOUNCEMENT.EVIDENCE";
    public static final String EVIDENCE_EXPORT_PERMISSION = "OPS.ANNOUNCEMENT.EVIDENCE_EXPORT";

    private final AnnouncementReceiptMapper receiptMapper;
    private final RequestContextService contextService;
    private final NotificationSecurityAuditService auditService;

    public AnnouncementStatistics statistics(String versionId, LocalDateTime now) {
        AnnouncementStatistics statistics = receiptMapper.statistics(
                contextService.tenantId(), versionId, now);
        if (statistics == null) {
            throw new BizException(45201, "ANNOUNCEMENT_VERSION_NOT_FOUND");
        }
        return statistics;
    }

    @Transactional
    public List<AnnouncementEvidenceRow> evidence(String versionId,
                                                  LocalDateTime now,
                                                  int page,
                                                  int size) {
        if (!contextService.hasPermission(EVIDENCE_PERMISSION)) {
            throw new BizException(45225, "ANNOUNCEMENT_EVIDENCE_FORBIDDEN");
        }
        int safeSize = Math.min(Math.max(size, 1), 200);
        long offset = (long) Math.max(page - 1, 0) * safeSize;
        List<AnnouncementEvidenceRow> rows = receiptMapper.evidence(
                contextService.tenantId(), versionId, now, safeSize, offset);
        auditService.record("RECIPIENT_EVIDENCE_VIEW", "ANNOUNCEMENT_VERSION", versionId,
                "PAGE=" + Math.max(page, 1) + ":SIZE=" + safeSize + ":COUNT=" + rows.size());
        return rows;
    }

    /** 导出需要独立于页面查看的权限，并把实际导出数量写入同事务审计。 */
    @Transactional
    public List<AnnouncementEvidenceRow> exportEvidence(String versionId, LocalDateTime now,
                                                        int requestedLimit) {
        if (!contextService.hasPermission(EVIDENCE_PERMISSION)
                || !contextService.hasPermission(EVIDENCE_EXPORT_PERMISSION)) {
            throw new BizException(45226, "ANNOUNCEMENT_EVIDENCE_EXPORT_FORBIDDEN");
        }
        int limit = Math.min(Math.max(requestedLimit, 1), 5_000);
        List<AnnouncementEvidenceRow> rows = receiptMapper.evidence(
                contextService.tenantId(), versionId, now, limit, 0);
        auditService.record("RECIPIENT_EVIDENCE_EXPORT", "ANNOUNCEMENT_VERSION", versionId,
                "LIMIT=" + limit + ":COUNT=" + rows.size());
        return rows;
    }
}
