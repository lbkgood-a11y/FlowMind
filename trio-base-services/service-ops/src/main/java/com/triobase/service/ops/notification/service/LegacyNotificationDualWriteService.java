package com.triobase.service.ops.notification.service;

import com.triobase.service.ops.notification.mapper.LegacyNotificationDualWriteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * legacy 写事务中的 v2 兼容投影器。
 *
 * <p>只有全局开关与租户名单同时命中才写入；任一 v2 写失败会回滚同事务中的 legacy 写，避免试点期间形成
 * 无法解释的静默差异。事故回滚通过关闭开关停止后续投影，已写 v2 数据保持只读可审计。</p>
 */
@Service
@RequiredArgsConstructor
public class LegacyNotificationDualWriteService {

    private final NotificationCutoverService cutoverService;
    private final LegacyNotificationDualWriteMapper mapper;

    public void announcementChanged(String tenantId, String announcementId, boolean draft) {
        if (!cutoverService.dualWriteEnabled(tenantId)) return;
        mapper.upsertAnnouncementIdentity(tenantId, announcementId);
        mapper.upsertAnnouncementVersion(tenantId, announcementId);
        mapper.syncAnnouncementWithdrawal(tenantId, announcementId);
        mapper.bindAnnouncementVersion(tenantId, announcementId);
        if (draft) {
            mapper.deleteDraftAudienceRules(tenantId, announcementId);
        }
        mapper.insertAnnouncementAudienceRules(tenantId, announcementId);
    }

    public void announcementRead(String tenantId, String readId) {
        if (cutoverService.dualWriteEnabled(tenantId)) {
            mapper.upsertAnnouncementRead(tenantId, readId);
        }
    }

    public void messageCreated(String tenantId, String messageId) {
        if (!cutoverService.dualWriteEnabled(tenantId)) return;
        mapper.insertMessageTask(tenantId, messageId);
        mapper.upsertMessageProjections(tenantId, messageId);
        mapper.mapMessage(tenantId, messageId);
    }

    public void recipientChanged(String tenantId, String recipientId) {
        if (cutoverService.dualWriteEnabled(tenantId)) {
            mapper.syncRecipientState(tenantId, recipientId);
        }
    }
}
