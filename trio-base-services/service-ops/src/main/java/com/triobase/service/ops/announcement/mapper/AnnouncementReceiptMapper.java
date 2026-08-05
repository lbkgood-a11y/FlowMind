package com.triobase.service.ops.announcement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.announcement.dto.AnnouncementEvidenceRow;
import com.triobase.service.ops.announcement.dto.AnnouncementStatistics;
import com.triobase.service.ops.announcement.entity.AnnouncementReceiptEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AnnouncementReceiptMapper extends BaseMapper<AnnouncementReceiptEntity> {

    /**
     * 返回所有租户当前逾期且尚未确认的收件人数，仅用于无租户标签的运维聚合指标。
     * 指标消费者不得据此推断或导出收件人身份。
     */
    @Select("""
            SELECT count(*)
            FROM ops_announcement_recipient_snapshot s
            JOIN ops_announcement_version v
              ON v.tenant_id = s.tenant_id AND v.id = s.version_id
            LEFT JOIN ops_announcement_receipt r
              ON r.tenant_id = s.tenant_id AND r.version_id = s.version_id
             AND r.recipient_user_id = s.recipient_user_id
            WHERE v.confirmation_deadline IS NOT NULL
              AND v.confirmation_deadline &lt; CURRENT_TIMESTAMP
              AND r.confirmed_at IS NULL
            """)
    long countOverdueConfirmations();

    /** 幂等写入首次阅读时间；重复打开不得改写原始阅读证据。 */
    @Insert("""
            INSERT INTO ops_announcement_receipt(
                id, tenant_id, version_id, recipient_user_id, read_at, created_at, updated_at)
            VALUES(#{id}, #{tenantId}, #{versionId}, #{userId}, #{occurredAt}, #{occurredAt}, #{occurredAt})
            ON CONFLICT (tenant_id, version_id, recipient_user_id)
            DO UPDATE SET read_at = COALESCE(ops_announcement_receipt.read_at, EXCLUDED.read_at),
                          updated_at = CASE WHEN ops_announcement_receipt.read_at IS NULL
                                            THEN EXCLUDED.updated_at
                                            ELSE ops_announcement_receipt.updated_at END
            """)
    int markRead(@Param("id") String id,
                 @Param("tenantId") String tenantId,
                 @Param("versionId") String versionId,
                 @Param("userId") String userId,
                 @Param("occurredAt") LocalDateTime occurredAt);

    /** 首次确认同时补齐阅读时间；重复确认不得改写声明哈希、时间或 TraceId。 */
    @Insert("""
            INSERT INTO ops_announcement_receipt(
                id, tenant_id, version_id, recipient_user_id, read_at, confirmed_at,
                confirmation_statement_hash, confirmation_trace_id, created_at, updated_at)
            VALUES(#{id}, #{tenantId}, #{versionId}, #{userId}, #{occurredAt}, #{occurredAt},
                   #{statementHash}, #{traceId}, #{occurredAt}, #{occurredAt})
            ON CONFLICT (tenant_id, version_id, recipient_user_id)
            DO UPDATE SET read_at = COALESCE(ops_announcement_receipt.read_at, EXCLUDED.read_at),
                          confirmed_at = COALESCE(ops_announcement_receipt.confirmed_at, EXCLUDED.confirmed_at),
                          confirmation_statement_hash = COALESCE(
                              ops_announcement_receipt.confirmation_statement_hash,
                              EXCLUDED.confirmation_statement_hash),
                          confirmation_trace_id = COALESCE(
                              ops_announcement_receipt.confirmation_trace_id,
                              EXCLUDED.confirmation_trace_id),
                          updated_at = CASE WHEN ops_announcement_receipt.confirmed_at IS NULL
                                            THEN EXCLUDED.updated_at
                                            ELSE ops_announcement_receipt.updated_at END
            """)
    int confirm(@Param("id") String id,
                @Param("tenantId") String tenantId,
                @Param("versionId") String versionId,
                @Param("userId") String userId,
                @Param("occurredAt") LocalDateTime occurredAt,
                @Param("statementHash") String statementHash,
                @Param("traceId") String traceId);

    @Select("""
            SELECT
                (SELECT count(*) FROM ops_announcement_recipient_snapshot s
                  WHERE s.tenant_id = #{tenantId} AND s.version_id = #{versionId}) AS accountableCount,
                count(r.read_at) AS readCount,
                count(r.confirmed_at) AS confirmedCount,
                count(s.recipient_user_id) FILTER (WHERE v.confirmation_deadline IS NOT NULL
                                                        AND v.confirmation_deadline &lt; #{now}
                                                        AND r.confirmed_at IS NULL) AS overdueCount,
                #{now} AS calculatedAt
            FROM ops_announcement_version v
            LEFT JOIN ops_announcement_recipient_snapshot s
              ON s.tenant_id = v.tenant_id AND s.version_id = v.id
            LEFT JOIN ops_announcement_receipt r
              ON r.tenant_id = s.tenant_id AND r.version_id = s.version_id
             AND r.recipient_user_id = s.recipient_user_id
            WHERE v.tenant_id = #{tenantId} AND v.id = #{versionId}
            GROUP BY v.id
            """)
    AnnouncementStatistics statistics(@Param("tenantId") String tenantId,
                                      @Param("versionId") String versionId,
                                      @Param("now") LocalDateTime now);

    @Select("""
            SELECT s.recipient_user_id AS recipientUserId,
                   r.read_at AS readAt,
                   r.confirmed_at AS confirmedAt,
                   CASE WHEN v.confirmation_deadline IS NOT NULL
                             AND v.confirmation_deadline &lt; #{now}
                             AND r.confirmed_at IS NULL THEN TRUE ELSE FALSE END AS overdue
            FROM ops_announcement_recipient_snapshot s
            JOIN ops_announcement_version v
              ON v.tenant_id = s.tenant_id AND v.id = s.version_id
            LEFT JOIN ops_announcement_receipt r
              ON r.tenant_id = s.tenant_id AND r.version_id = s.version_id
             AND r.recipient_user_id = s.recipient_user_id
            WHERE s.tenant_id = #{tenantId} AND s.version_id = #{versionId}
            ORDER BY s.recipient_user_id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AnnouncementEvidenceRow> evidence(@Param("tenantId") String tenantId,
                                           @Param("versionId") String versionId,
                                           @Param("now") LocalDateTime now,
                                           @Param("limit") int limit,
                                           @Param("offset") long offset);
}
