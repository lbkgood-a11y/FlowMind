package com.triobase.service.ops.notification.mapper;

import com.triobase.service.ops.notification.entity.NotificationRetentionPolicyEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知保留仓储契约。
 *
 * <p>调用方每次只能传入一个租户；所有变更使用 id 有界子查询，并在同一 SQL 内重新检查活动冻结，
 * 防止候选选择和删除之间新增冻结时误清理。</p>
 */
@Mapper
public interface NotificationRetentionMapper {

    @Select("""
            SELECT * FROM ops_notification_retention_policy
            WHERE enabled = 1 ORDER BY tenant_id LIMIT #{limit}
            """)
    List<NotificationRetentionPolicyEntity> findEnabledPolicies(@Param("limit") int limit);

    @Delete("""
            DELETE FROM ops_inbox_projection WHERE tenant_id = #{tenantId} AND id IN (
              SELECT p.id FROM ops_inbox_projection p
              WHERE p.tenant_id = #{tenantId} AND p.received_at < #{cutoff}
                AND NOT EXISTS (SELECT 1 FROM ops_notification_retention_hold h
                  WHERE h.tenant_id = p.tenant_id AND h.scope_type = 'TASK' AND h.scope_id = p.task_id
                    AND h.released_at IS NULL AND (h.expires_at IS NULL OR h.expires_at > #{now}))
              ORDER BY p.received_at, p.id LIMIT #{limit})
            """)
    int purgeProjections(@Param("tenantId") String tenantId, @Param("cutoff") LocalDateTime cutoff,
                         @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Delete("""
            DELETE FROM ops_announcement_receipt WHERE tenant_id = #{tenantId} AND id IN (
              SELECT r.id FROM ops_announcement_receipt r
              JOIN ops_announcement_version v ON v.tenant_id = r.tenant_id AND v.id = r.version_id
              WHERE r.tenant_id = #{tenantId} AND r.updated_at < #{cutoff}
                AND NOT EXISTS (SELECT 1 FROM ops_notification_retention_hold h
                  WHERE h.tenant_id = r.tenant_id AND h.scope_type = 'ANNOUNCEMENT'
                    AND h.scope_id = v.announcement_id AND h.released_at IS NULL
                    AND (h.expires_at IS NULL OR h.expires_at > #{now}))
              ORDER BY r.updated_at, r.id LIMIT #{limit})
            """)
    int purgeReceipts(@Param("tenantId") String tenantId, @Param("cutoff") LocalDateTime cutoff,
                      @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Delete("""
            DELETE FROM ops_notification_delivery_attempt WHERE tenant_id = #{tenantId} AND id IN (
              SELECT a.id FROM ops_notification_delivery_attempt a
              WHERE a.tenant_id = #{tenantId} AND a.occurred_at < #{cutoff}
                AND NOT EXISTS (SELECT 1 FROM ops_notification_retention_hold h
                  WHERE h.tenant_id = a.tenant_id AND h.scope_type = 'TASK' AND h.scope_id = a.task_id
                    AND h.released_at IS NULL AND (h.expires_at IS NULL OR h.expires_at > #{now}))
              ORDER BY a.occurred_at, a.id LIMIT #{limit})
            """)
    int purgeDeliveries(@Param("tenantId") String tenantId, @Param("cutoff") LocalDateTime cutoff,
                        @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE ops_announcement_version SET title = '已按保留策略匿名化', content = '',
              confirmation_statement = NULL, retention_anonymized_at = #{now}, updated_at = #{now}
            WHERE tenant_id = #{tenantId} AND id IN (
              SELECT v.id FROM ops_announcement_version v
              WHERE v.tenant_id = #{tenantId} AND v.updated_at < #{cutoff}
                AND v.lifecycle_state IN ('EXPIRED','WITHDRAWN','SUPERSEDED')
                AND v.retention_anonymized_at IS NULL
                AND NOT EXISTS (SELECT 1 FROM ops_notification_retention_hold h
                  WHERE h.tenant_id = v.tenant_id AND h.scope_type = 'ANNOUNCEMENT'
                    AND h.scope_id = v.announcement_id AND h.released_at IS NULL
                    AND (h.expires_at IS NULL OR h.expires_at > #{now}))
              ORDER BY v.updated_at, v.id LIMIT #{limit})
            """)
    int anonymizeAnnouncements(@Param("tenantId") String tenantId,
                               @Param("cutoff") LocalDateTime cutoff,
                               @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Delete("""
            DELETE FROM ops_notification_config_audit WHERE tenant_id = #{tenantId} AND id IN (
              SELECT a.id FROM ops_notification_config_audit a
              WHERE a.tenant_id = #{tenantId} AND a.occurred_at < #{cutoff}
                AND NOT EXISTS (SELECT 1 FROM ops_notification_retention_hold h
                  WHERE h.tenant_id = a.tenant_id AND h.scope_type = 'AUDIT' AND h.scope_id = a.id
                    AND h.released_at IS NULL AND (h.expires_at IS NULL OR h.expires_at > #{now}))
              ORDER BY a.occurred_at, a.id LIMIT #{limit})
            """)
    int purgeAudits(@Param("tenantId") String tenantId, @Param("cutoff") LocalDateTime cutoff,
                    @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Delete("""
            DELETE FROM ops_notification_security_audit WHERE tenant_id = #{tenantId} AND id IN (
              SELECT a.id FROM ops_notification_security_audit a
              WHERE a.tenant_id = #{tenantId} AND a.occurred_at < #{cutoff}
                AND NOT EXISTS (SELECT 1 FROM ops_notification_retention_hold h
                  WHERE h.tenant_id = a.tenant_id AND h.scope_type = 'AUDIT' AND h.scope_id = a.id
                    AND h.released_at IS NULL AND (h.expires_at IS NULL OR h.expires_at > #{now}))
              ORDER BY a.occurred_at, a.id LIMIT #{limit})
            """)
    int purgeSecurityAudits(@Param("tenantId") String tenantId,
                            @Param("cutoff") LocalDateTime cutoff,
                            @Param("now") LocalDateTime now, @Param("limit") int limit);
}
