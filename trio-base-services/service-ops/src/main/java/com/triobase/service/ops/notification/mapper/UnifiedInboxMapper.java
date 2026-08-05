package com.triobase.service.ops.notification.mapper;

import com.triobase.service.ops.notification.dto.UnifiedInboxRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 以数据库集合运算合并个人投影与公告，避免按公告逐条查询阅读状态。 */
@Mapper
public interface UnifiedInboxMapper {

    @Select("""
            <script>
            WITH inbox_items AS (
                SELECT p.id, p.item_type, p.title, p.summary, p.received_at, p.read_at,
                       p.archived_at, (p.withdrawn_at IS NOT NULL) AS withdrawn,
                       (p.action_id IS NOT NULL) AS task_related, p.source_owner,
                       p.resource_type, p.resource_id, p.resource_key, p.action_id
                FROM ops_inbox_projection p
                WHERE p.tenant_id = #{tenantId} AND p.recipient_user_id = #{userId}
                  AND p.hidden_at IS NULL
                UNION ALL
                SELECT v.id, 'ANNOUNCEMENT', v.title, LEFT(v.content, 512), v.published_at,
                       r.read_at, NULL, FALSE, FALSE, 'service-ops', 'ANNOUNCEMENT',
                       v.announcement_id, NULL, NULL
                FROM ops_announcement_version v
                LEFT JOIN ops_announcement_receipt r
                  ON r.tenant_id = v.tenant_id AND r.version_id = v.id
                 AND r.recipient_user_id = #{userId}
                WHERE v.tenant_id = #{tenantId} AND v.lifecycle_state = 'PUBLISHED'
                  AND v.published_at &lt;= #{now}
                  AND (v.effective_until IS NULL OR v.effective_until &gt; #{now})
                  AND (
                    EXISTS (SELECT 1 FROM ops_announcement_recipient_snapshot s
                            WHERE s.tenant_id = v.tenant_id AND s.version_id = v.id
                              AND s.recipient_user_id = #{userId})
                    OR (v.audience_mode = 'DYNAMIC' AND EXISTS (
                        SELECT 1 FROM ops_announcement_audience_rule ar
                        WHERE ar.tenant_id = v.tenant_id AND ar.version_id = v.id AND (
                            ar.selector_type = 'ALL'
                            OR (ar.selector_type = 'USER' AND ar.subject_id = #{userId})
                            <if test='orgIds != null and !orgIds.isEmpty()'>
                            OR (ar.selector_type = 'ORGANIZATION' AND ar.subject_id IN
                                <foreach collection='orgIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
                            </if>
                            <if test='roleIds != null and !roleIds.isEmpty()'>
                            OR (ar.selector_type = 'ROLE' AND ar.subject_id IN
                                <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
                            </if>
                        )))
                  )
            )
            SELECT * FROM inbox_items
            WHERE (#{itemType} IS NULL OR item_type = #{itemType})
              AND (#{readState} IS NULL OR (#{readState} = 'UNREAD' AND read_at IS NULL)
                   OR (#{readState} = 'READ' AND read_at IS NOT NULL))
              AND (#{sourceOwner} IS NULL OR source_owner = #{sourceOwner})
              AND (#{fromTime} IS NULL OR received_at &gt;= #{fromTime})
              AND (#{toTime} IS NULL OR received_at &lt; #{toTime})
            ORDER BY received_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<UnifiedInboxRow> find(@Param("tenantId") String tenantId,
                               @Param("userId") String userId,
                               @Param("orgIds") List<String> orgIds,
                               @Param("roleIds") List<String> roleIds,
                               @Param("now") LocalDateTime now,
                               @Param("itemType") String itemType,
                               @Param("readState") String readState,
                               @Param("sourceOwner") String sourceOwner,
                               @Param("fromTime") LocalDateTime fromTime,
                               @Param("toTime") LocalDateTime toTime,
                               @Param("limit") int limit,
                               @Param("offset") long offset);
}
