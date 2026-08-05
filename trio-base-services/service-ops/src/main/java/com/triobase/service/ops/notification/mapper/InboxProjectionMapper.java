package com.triobase.service.ops.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.notification.entity.InboxProjectionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import java.time.LocalDateTime;

@Mapper
public interface InboxProjectionMapper extends BaseMapper<InboxProjectionEntity> {

    @Insert("""
            INSERT INTO ops_inbox_projection
                (id, tenant_id, task_id, recipient_user_id, channel_code, item_type, title, summary,
                 source_owner, resource_type, resource_id, resource_key, action_id, received_at,
                 expires_at, created_at, updated_at)
            VALUES
                (#{id}, #{tenantId}, #{taskId}, #{recipientUserId}, #{channelCode}, #{itemType},
                 #{title}, #{summary}, #{sourceOwner}, #{resourceType}, #{resourceId}, #{resourceKey},
                 #{actionId}, #{receivedAt}, #{expiresAt}, #{createdAt}, #{updatedAt})
            ON CONFLICT (tenant_id, task_id, recipient_user_id, channel_code) DO NOTHING
            """)
    int insertIgnore(InboxProjectionEntity projection);

    @Update("""
            UPDATE ops_inbox_projection
            SET withdrawn_at = COALESCE(withdrawn_at, #{withdrawnAt}), updated_at = #{withdrawnAt}
            WHERE tenant_id = #{tenantId} AND task_id = #{taskId} AND withdrawn_at IS NULL
            """)
    int withdrawTask(@Param("tenantId") String tenantId,
                     @Param("taskId") String taskId,
                     @Param("withdrawnAt") LocalDateTime withdrawnAt);

    @Select("""
            SELECT recipient_user_id FROM ops_inbox_projection
            WHERE tenant_id = #{tenantId} AND task_id = #{taskId} AND withdrawn_at IS NULL
            """)
    List<String> findActiveRecipientIds(@Param("tenantId") String tenantId,
                                        @Param("taskId") String taskId);

    @Select("""
            SELECT * FROM ops_inbox_projection
            WHERE tenant_id = #{tenantId} AND recipient_user_id = #{userId}
              AND hidden_at IS NULL
            ORDER BY received_at DESC, id DESC LIMIT #{limit}
            """)
    List<InboxProjectionEntity> findRecent(@Param("tenantId") String tenantId,
                                           @Param("userId") String userId,
                                           @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM ops_inbox_projection
            WHERE tenant_id = #{tenantId} AND recipient_user_id = #{userId}
              AND hidden_at IS NULL AND read_at IS NULL AND withdrawn_at IS NULL
            """)
    long unreadCount(@Param("tenantId") String tenantId, @Param("userId") String userId);

    @Select("""
            SELECT * FROM ops_inbox_projection
            WHERE tenant_id = #{tenantId} AND recipient_user_id = #{userId} AND id = #{id}
            """)
    InboxProjectionEntity findOwned(@Param("tenantId") String tenantId,
                                    @Param("userId") String userId,
                                    @Param("id") String id);

    @Update("""
            UPDATE ops_inbox_projection SET read_at = COALESCE(read_at, #{now}), updated_at = #{now}
            WHERE tenant_id = #{tenantId} AND recipient_user_id = #{userId}
              AND id = #{id} AND hidden_at IS NULL
            """)
    int markRead(@Param("tenantId") String tenantId, @Param("userId") String userId,
                 @Param("id") String id, @Param("now") LocalDateTime now);

    @Update("""
            <script>
            UPDATE ops_inbox_projection SET read_at = COALESCE(read_at, #{now}), updated_at = #{now}
            WHERE tenant_id = #{tenantId} AND recipient_user_id = #{userId}
              AND hidden_at IS NULL AND id IN
              <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int markReadBatch(@Param("tenantId") String tenantId, @Param("userId") String userId,
                      @Param("ids") List<String> ids, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ops_inbox_projection SET read_at = COALESCE(read_at, #{now}), updated_at = #{now}
            WHERE tenant_id = #{tenantId} AND recipient_user_id = #{userId}
              AND hidden_at IS NULL AND read_at IS NULL
              AND (received_at &lt; #{receivedAt} OR (received_at = #{receivedAt} AND id &lt;= #{boundaryId}))
            """)
    int markAllRead(@Param("tenantId") String tenantId, @Param("userId") String userId,
                    @Param("receivedAt") LocalDateTime receivedAt,
                    @Param("boundaryId") String boundaryId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ops_inbox_projection SET archived_at = COALESCE(archived_at, #{now}), updated_at = #{now}
            WHERE tenant_id = #{tenantId} AND recipient_user_id = #{userId} AND id = #{id}
              AND hidden_at IS NULL
            """)
    int archive(@Param("tenantId") String tenantId, @Param("userId") String userId,
                @Param("id") String id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ops_inbox_projection SET archived_at = NULL, updated_at = #{now}
            WHERE tenant_id = #{tenantId} AND recipient_user_id = #{userId} AND id = #{id}
              AND hidden_at IS NULL
            """)
    int restore(@Param("tenantId") String tenantId, @Param("userId") String userId,
                @Param("id") String id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ops_inbox_projection SET hidden_at = COALESCE(hidden_at, #{now}), updated_at = #{now}
            WHERE tenant_id = #{tenantId} AND recipient_user_id = #{userId} AND id = #{id}
            """)
    int hide(@Param("tenantId") String tenantId, @Param("userId") String userId,
             @Param("id") String id, @Param("now") LocalDateTime now);
}
