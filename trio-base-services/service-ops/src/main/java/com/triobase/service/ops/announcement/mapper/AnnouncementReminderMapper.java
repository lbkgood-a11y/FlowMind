package com.triobase.service.ops.announcement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.announcement.entity.AnnouncementReminderEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AnnouncementReminderMapper extends BaseMapper<AnnouncementReminderEntity> {

    @Select("""
            SELECT s.recipient_user_id
            FROM ops_announcement_recipient_snapshot s
            LEFT JOIN ops_announcement_receipt r
              ON r.tenant_id = s.tenant_id AND r.version_id = s.version_id
             AND r.recipient_user_id = s.recipient_user_id
            WHERE s.tenant_id = #{tenantId} AND s.version_id = #{versionId}
              AND ((#{mode} = 'UNREAD' AND r.read_at IS NULL)
                   OR (#{mode} = 'UNCONFIRMED' AND r.confirmed_at IS NULL))
              AND s.recipient_user_id &gt; #{afterUserId}
            ORDER BY s.recipient_user_id
            LIMIT #{limit}
            """)
    List<String> eligibleRecipients(@Param("tenantId") String tenantId,
                                    @Param("versionId") String versionId,
                                    @Param("mode") String mode,
                                    @Param("afterUserId") String afterUserId,
                                    @Param("limit") int limit);

    @Insert("""
            INSERT INTO ops_announcement_reminder(
                id, tenant_id, version_id, recipient_user_id, reminder_key,
                requested_by, trace_id, requested_at)
            VALUES(#{id}, #{tenantId}, #{versionId}, #{recipientUserId}, #{reminderKey},
                   #{requestedBy}, #{traceId}, #{requestedAt})
            ON CONFLICT (tenant_id, reminder_key, recipient_user_id) DO NOTHING
            """)
    int insertIgnore(AnnouncementReminderEntity entity);

    @Update("""
            UPDATE ops_announcement_reminder
            SET notification_task_id = #{taskId}
            WHERE tenant_id = #{tenantId} AND reminder_key = #{reminderKey}
              AND recipient_user_id = #{userId} AND notification_task_id IS NULL
            """)
    int bindTask(@Param("tenantId") String tenantId,
                 @Param("reminderKey") String reminderKey,
                 @Param("userId") String userId,
                 @Param("taskId") String taskId);
}
