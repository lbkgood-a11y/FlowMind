package com.triobase.service.ops.announcement.service;

import com.triobase.service.ops.announcement.entity.AnnouncementVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告可见性的集合查询。
 *
 * <p>orgIds 和 roleIds 必须由对应 Owner 预先按当前用户权限解析；空集合不是“全部”。动态参与者
 * 未被当前查询隐式放行，必须使用冻结快照或由注册解析器产生受控投影后再加入查询。</p>
 */
@Mapper
public interface AnnouncementVisibilityMapper {

    @Select("""
            <script>
            SELECT DISTINCT v.*
            FROM ops_announcement_version v
            WHERE v.tenant_id = #{tenantId}
              AND v.lifecycle_state = 'PUBLISHED'
              AND (v.published_at IS NULL OR v.published_at &lt;= #{now})
              AND (v.effective_until IS NULL OR v.effective_until &gt; #{now})
              AND (
                EXISTS (SELECT 1 FROM ops_announcement_recipient_snapshot s
                         WHERE s.tenant_id = v.tenant_id AND s.version_id = v.id
                           AND s.recipient_user_id = #{userId})
                OR (v.audience_mode = 'DYNAMIC' AND EXISTS (
                    SELECT 1 FROM ops_announcement_audience_rule r
                    WHERE r.tenant_id = v.tenant_id AND r.version_id = v.id AND (
                        r.selector_type = 'ALL'
                        OR (r.selector_type = 'USER' AND r.subject_id = #{userId})
                        <if test='orgIds != null and !orgIds.isEmpty()'>
                        OR (r.selector_type = 'ORGANIZATION' AND r.subject_id IN
                            <foreach collection='orgIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
                        </if>
                        <if test='roleIds != null and !roleIds.isEmpty()'>
                        OR (r.selector_type = 'ROLE' AND r.subject_id IN
                            <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
                        </if>
                    )))
              )
            ORDER BY CASE WHEN v.pin_from IS NOT NULL AND v.pin_from &lt;= #{now}
                                AND (v.pin_until IS NULL OR v.pin_until &gt; #{now}) THEN 0 ELSE 1 END,
                     v.published_at DESC, v.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AnnouncementVersionEntity> visible(@Param("tenantId") String tenantId,
                                             @Param("userId") String userId,
                                             @Param("orgIds") List<String> orgIds,
                                             @Param("roleIds") List<String> roleIds,
                                             @Param("now") LocalDateTime now,
                                             @Param("limit") int limit,
                                             @Param("offset") long offset);

    @Select("""
            <script>
            SELECT EXISTS(
                SELECT 1 FROM ops_announcement_version v
                WHERE v.tenant_id = #{tenantId} AND v.id = #{versionId}
                  AND v.lifecycle_state = 'PUBLISHED'
                  AND (
                    EXISTS (SELECT 1 FROM ops_announcement_recipient_snapshot s
                             WHERE s.tenant_id = v.tenant_id AND s.version_id = v.id
                               AND s.recipient_user_id = #{userId})
                    OR (v.audience_mode = 'DYNAMIC' AND EXISTS (
                        SELECT 1 FROM ops_announcement_audience_rule r
                        WHERE r.tenant_id = v.tenant_id AND r.version_id = v.id AND (
                            r.selector_type = 'ALL'
                            OR (r.selector_type = 'USER' AND r.subject_id = #{userId})
                            <if test='orgIds != null and !orgIds.isEmpty()'>
                            OR (r.selector_type = 'ORGANIZATION' AND r.subject_id IN
                                <foreach collection='orgIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
                            </if>
                            <if test='roleIds != null and !roleIds.isEmpty()'>
                            OR (r.selector_type = 'ROLE' AND r.subject_id IN
                                <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
                            </if>
                        )))
                  ))
            </script>
            """)
    boolean isVisible(@Param("tenantId") String tenantId,
                      @Param("versionId") String versionId,
                      @Param("userId") String userId,
                      @Param("orgIds") List<String> orgIds,
                      @Param("roleIds") List<String> roleIds);

    @Select("""
            SELECT EXISTS(SELECT 1 FROM ops_announcement_recipient_snapshot
                           WHERE tenant_id = #{tenantId} AND version_id = #{versionId}
                             AND recipient_user_id = #{userId})
            """)
    boolean isSnapshotRecipient(@Param("tenantId") String tenantId,
                                @Param("versionId") String versionId,
                                @Param("userId") String userId);
}
