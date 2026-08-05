package com.triobase.service.ops.notification.service;

import com.triobase.service.ops.notification.dto.NotificationRetentionResult;
import com.triobase.service.ops.notification.entity.NotificationRetentionPolicyEntity;
import com.triobase.service.ops.notification.mapper.NotificationRetentionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 执行单租户通知证据保留批次。
 *
 * <p>每类数据每次最多处理 5000 行，失败时当前租户批次整体回滚；不同租户由调度入口分开调用，
 * 避免一个租户的异常扩大为跨租户长事务。公告到期只匿名化正文，继续保留状态和关联证据。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationRetentionService {

    static final int MAX_BATCH_SIZE = 5_000;
    private final NotificationRetentionMapper mapper;

    @Transactional
    public NotificationRetentionResult purgeTenant(NotificationRetentionPolicyEntity policy,
                                                    LocalDateTime now) {
        requirePolicy(policy);
        int limit = Math.min(Math.max(policy.getPurgeBatchSize(), 1), MAX_BATCH_SIZE);
        String tenantId = policy.getTenantId();
        int projections = mapper.purgeProjections(tenantId,
                now.minusDays(policy.getProjectionDays()), now, limit);
        int receipts = mapper.purgeReceipts(tenantId,
                now.minusDays(policy.getReceiptDays()), now, limit);
        int deliveries = mapper.purgeDeliveries(tenantId,
                now.minusDays(policy.getDeliveryDays()), now, limit);
        int announcements = mapper.anonymizeAnnouncements(tenantId,
                now.minusDays(policy.getAnnouncementDays()), now, limit);
        LocalDateTime auditCutoff = now.minusDays(policy.getAuditDays());
        int audits = mapper.purgeAudits(tenantId, auditCutoff, now, limit)
                + mapper.purgeSecurityAudits(tenantId, auditCutoff, now, limit);
        return new NotificationRetentionResult(tenantId, projections, receipts,
                deliveries, announcements, audits);
    }

    private void requirePolicy(NotificationRetentionPolicyEntity policy) {
        if (policy == null || policy.getTenantId() == null || policy.getTenantId().isBlank()
                || policy.getProjectionDays() == null || policy.getReceiptDays() == null
                || policy.getDeliveryDays() == null || policy.getAnnouncementDays() == null
                || policy.getAuditDays() == null || policy.getPurgeBatchSize() == null
                || policy.getProjectionDays() < 1 || policy.getReceiptDays() < 1
                || policy.getDeliveryDays() < 1 || policy.getAnnouncementDays() < 1
                || policy.getAuditDays() < 1) {
            throw new IllegalArgumentException("NOTIFICATION_RETENTION_POLICY_INVALID");
        }
    }
}
