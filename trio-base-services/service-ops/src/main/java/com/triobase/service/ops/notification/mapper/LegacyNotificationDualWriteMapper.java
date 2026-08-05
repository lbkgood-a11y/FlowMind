package com.triobase.service.ops.notification.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 将 legacy Owner 表中的单条事实幂等投影到 v2 通知结构。
 *
 * <p>所有语句都在调用方 legacy 事务中执行，使用稳定派生 ID 和唯一键防重。回滚只停止调用本 Mapper，
 * 不提供反向删除方法，确保已经形成的投递、阅读和迁移证据继续可审计。</p>
 */
@Mapper
public interface LegacyNotificationDualWriteMapper {

    @Insert("""
            INSERT INTO ops_announcement_identity(
                id, tenant_id, announcement_code, legacy_id, created_at, updated_at)
            SELECT upper(md5(tenant_id || ':announcement:' || id)), tenant_id,
                   'LEGACY-' || id, id, created_at, updated_at
            FROM ops_announcement WHERE tenant_id = #{tenantId} AND id = #{legacyId}
            ON CONFLICT (tenant_id, legacy_id) DO UPDATE SET updated_at = EXCLUDED.updated_at
            """)
    int upsertAnnouncementIdentity(@Param("tenantId") String tenantId,
                                   @Param("legacyId") String legacyId);

    @Insert("""
            INSERT INTO ops_announcement_version(
                id, tenant_id, announcement_id, version_no, title, content, priority,
                lifecycle_state, audience_mode, confirmation_required, published_at,
                effective_until, created_at, updated_at)
            SELECT upper(md5(a.tenant_id || ':announcement-version:' || a.id || ':1')),
                   a.tenant_id, i.id, 1, a.title, a.content, a.priority,
                   CASE a.status WHEN 'PUBLISHED' THEN 'PUBLISHED'
                                 WHEN 'OFFLINE' THEN 'WITHDRAWN' ELSE 'DRAFT' END,
                   'DYNAMIC', 0, a.publish_at, a.unpublish_at, a.created_at, a.updated_at
            FROM ops_announcement a
            JOIN ops_announcement_identity i
              ON i.tenant_id = a.tenant_id AND i.legacy_id = a.id
            WHERE a.tenant_id = #{tenantId} AND a.id = #{legacyId}
            ON CONFLICT (tenant_id, announcement_id, version_no) DO UPDATE
            SET title = EXCLUDED.title, content = EXCLUDED.content, priority = EXCLUDED.priority,
                lifecycle_state = EXCLUDED.lifecycle_state, published_at = EXCLUDED.published_at,
                effective_until = EXCLUDED.effective_until, updated_at = EXCLUDED.updated_at
            WHERE ops_announcement_version.lifecycle_state = 'DRAFT'
            """)
    int upsertAnnouncementVersion(@Param("tenantId") String tenantId,
                                  @Param("legacyId") String legacyId);

    /** 发布后仅同步生命周期时间，禁止 legacy 编辑覆盖已发布 v2 正文证据。 */
    @Update("""
            UPDATE ops_announcement_version v
            SET lifecycle_state = CASE a.status WHEN 'OFFLINE' THEN 'WITHDRAWN'
                                                   ELSE v.lifecycle_state END,
                effective_until = CASE WHEN a.status = 'OFFLINE' THEN a.unpublish_at
                                       ELSE v.effective_until END,
                updated_at = a.updated_at
            FROM ops_announcement a, ops_announcement_identity i
            WHERE a.tenant_id = #{tenantId} AND a.id = #{legacyId}
              AND i.tenant_id = a.tenant_id AND i.legacy_id = a.id
              AND v.tenant_id = i.tenant_id AND v.announcement_id = i.id AND v.version_no = 1
              AND v.lifecycle_state = 'PUBLISHED' AND a.status = 'OFFLINE'
            """)
    int syncAnnouncementWithdrawal(@Param("tenantId") String tenantId,
                                   @Param("legacyId") String legacyId);

    @Update("""
            UPDATE ops_announcement_identity i SET current_version_id = v.id
            FROM ops_announcement_version v
            WHERE i.tenant_id = #{tenantId} AND i.legacy_id = #{legacyId}
              AND v.tenant_id = i.tenant_id AND v.announcement_id = i.id AND v.version_no = 1
            """)
    int bindAnnouncementVersion(@Param("tenantId") String tenantId,
                                @Param("legacyId") String legacyId);

    @Delete("""
            DELETE FROM ops_announcement_audience_rule r
            USING ops_announcement_version v, ops_announcement_identity i
            WHERE i.tenant_id = #{tenantId} AND i.legacy_id = #{legacyId}
              AND v.tenant_id = i.tenant_id AND v.announcement_id = i.id AND v.version_no = 1
              AND v.lifecycle_state = 'DRAFT' AND r.tenant_id = v.tenant_id AND r.version_id = v.id
            """)
    int deleteDraftAudienceRules(@Param("tenantId") String tenantId,
                                 @Param("legacyId") String legacyId);

    @Insert("""
            INSERT INTO ops_announcement_audience_rule(
                id, tenant_id, version_id, selector_type, subject_id, include_descendants, created_at)
            SELECT upper(md5(a.tenant_id || ':announcement-rule:' || a.id || ':' ||
                   a.target_type || ':' || coalesce(target.subject_id, 'ALL'))),
                   a.tenant_id, v.id,
                   CASE a.target_type WHEN 'ORG' THEN 'ORGANIZATION'
                                      WHEN 'USER' THEN 'USER' ELSE 'ALL' END,
                   target.subject_id, 0, a.created_at
            FROM ops_announcement a
            JOIN ops_announcement_identity i
              ON i.tenant_id = a.tenant_id AND i.legacy_id = a.id
            JOIN ops_announcement_version v
              ON v.tenant_id = i.tenant_id AND v.announcement_id = i.id AND v.version_no = 1
            LEFT JOIN LATERAL (
                SELECT nullif(trim(value), '') subject_id
                FROM unnest(string_to_array(
                    CASE a.target_type WHEN 'ORG' THEN a.target_org_ids
                                       WHEN 'USER' THEN a.target_user_ids ELSE NULL END, ',')) value
            ) target ON a.target_type IN ('ORG','USER')
            WHERE a.tenant_id = #{tenantId} AND a.id = #{legacyId}
              AND (a.target_type = 'ALL' OR target.subject_id IS NOT NULL)
            ON CONFLICT (id) DO NOTHING
            """)
    int insertAnnouncementAudienceRules(@Param("tenantId") String tenantId,
                                        @Param("legacyId") String legacyId);

    @Insert("""
            INSERT INTO ops_announcement_receipt(
                id, tenant_id, version_id, recipient_user_id, read_at, created_at, updated_at)
            SELECT upper(md5(r.tenant_id || ':announcement-receipt:' || r.id)), r.tenant_id,
                   v.id, r.user_id, r.read_at, r.created_at, r.updated_at
            FROM ops_announcement_read r
            JOIN ops_announcement_identity i
              ON i.tenant_id = r.tenant_id AND i.legacy_id = r.announcement_id
            JOIN ops_announcement_version v
              ON v.tenant_id = i.tenant_id AND v.announcement_id = i.id AND v.version_no = 1
            WHERE r.tenant_id = #{tenantId} AND r.id = #{readId}
            ON CONFLICT (tenant_id, version_id, recipient_user_id) DO UPDATE
            SET read_at = coalesce(ops_announcement_receipt.read_at, EXCLUDED.read_at),
                updated_at = EXCLUDED.updated_at
            """)
    int upsertAnnouncementRead(@Param("tenantId") String tenantId,
                               @Param("readId") String readId);

    @Insert("""
            INSERT INTO ops_notification_task(
                id, tenant_id, producer, event_id, idempotency_key, schema_version,
                template_key, request_payload, task_state, audience_mode, resolved_count,
                delivered_count, failed_count, created_at, updated_at)
            SELECT upper(md5(m.tenant_id || ':message-task:' || m.id)), m.tenant_id,
                   coalesce(nullif(m.source_type, ''), 'legacy-service-ops'),
                   'legacy-message:' || m.id, 'legacy-message:' || m.id, '1.0',
                   'legacy-' || lower(m.message_type),
                   jsonb_build_object('legacyMessageId', m.id, 'messageType', m.message_type)::text,
                   'DELIVERED', 'FROZEN', count(r.id), count(r.id), 0, m.created_at, m.updated_at
            FROM ops_message m LEFT JOIN ops_message_recipient r
              ON r.tenant_id = m.tenant_id AND r.message_id = m.id
            WHERE m.tenant_id = #{tenantId} AND m.id = #{messageId}
            GROUP BY m.id, m.tenant_id, m.source_type, m.message_type, m.created_at, m.updated_at
            ON CONFLICT (tenant_id, producer, idempotency_key) DO NOTHING
            """)
    int insertMessageTask(@Param("tenantId") String tenantId,
                          @Param("messageId") String messageId);

    @Insert("""
            INSERT INTO ops_inbox_projection(
                id, tenant_id, task_id, recipient_user_id, channel_code, item_type, title,
                summary, source_owner, resource_type, resource_id, read_at, hidden_at,
                received_at, created_at, updated_at)
            SELECT upper(md5(r.tenant_id || ':message-projection:' || r.id)), r.tenant_id,
                   t.id, r.recipient_user_id, 'IN_APP', m.message_type, m.title,
                   left(m.content, 512), m.source_type, m.source_type, m.source_id,
                   r.read_at, r.deleted_at, r.created_at, r.created_at, r.updated_at
            FROM ops_message_recipient r
            JOIN ops_message m ON m.tenant_id = r.tenant_id AND m.id = r.message_id
            JOIN ops_notification_task t ON t.tenant_id = m.tenant_id
              AND t.idempotency_key = 'legacy-message:' || m.id
            WHERE r.tenant_id = #{tenantId} AND r.message_id = #{messageId}
            ON CONFLICT (tenant_id, task_id, recipient_user_id, channel_code) DO UPDATE
            SET read_at = EXCLUDED.read_at, hidden_at = EXCLUDED.hidden_at,
                updated_at = EXCLUDED.updated_at
            """)
    int upsertMessageProjections(@Param("tenantId") String tenantId,
                                 @Param("messageId") String messageId);

    @Insert("""
            INSERT INTO ops_notification_legacy_map(
                id, tenant_id, legacy_type, legacy_id, v2_type, v2_id)
            SELECT upper(md5(t.tenant_id || ':map:message:' || #{messageId})), t.tenant_id,
                   'MESSAGE', #{messageId}, 'NOTIFICATION_TASK', t.id
            FROM ops_notification_task t
            WHERE t.tenant_id = #{tenantId} AND t.idempotency_key = 'legacy-message:' || #{messageId}
            ON CONFLICT (tenant_id, legacy_type, legacy_id, v2_type) DO NOTHING
            """)
    int mapMessage(@Param("tenantId") String tenantId,
                   @Param("messageId") String messageId);

    @Update("""
            UPDATE ops_inbox_projection p SET read_at = r.read_at, hidden_at = r.deleted_at,
                updated_at = r.updated_at
            FROM ops_message_recipient r
            WHERE r.tenant_id = #{tenantId} AND r.id = #{recipientId}
              AND p.tenant_id = r.tenant_id
              AND p.id = upper(md5(r.tenant_id || ':message-projection:' || r.id))
            """)
    int syncRecipientState(@Param("tenantId") String tenantId,
                           @Param("recipientId") String recipientId);
}
