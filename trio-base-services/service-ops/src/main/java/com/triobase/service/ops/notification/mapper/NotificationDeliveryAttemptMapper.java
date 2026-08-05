package com.triobase.service.ops.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.notification.entity.NotificationDeliveryAttemptEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NotificationDeliveryAttemptMapper extends BaseMapper<NotificationDeliveryAttemptEntity> {

    @Insert("""
            INSERT INTO ops_notification_delivery_attempt
                (id, tenant_id, task_id, projection_id, recipient_user_id, channel_code, attempt_no,
                 delivery_status, retryable, error_category, sanitized_message, occurred_at)
            VALUES
                (#{id}, #{tenantId}, #{taskId}, #{projectionId}, #{recipientUserId}, #{channelCode},
                 #{attemptNo}, #{deliveryStatus}, #{retryable}, #{errorCategory}, #{sanitizedMessage},
                 #{occurredAt})
            ON CONFLICT (tenant_id, task_id, recipient_user_id, channel_code, attempt_no) DO NOTHING
            """)
    int insertIgnore(NotificationDeliveryAttemptEntity attempt);

    @Select("""
            SELECT * FROM ops_notification_delivery_attempt
            WHERE tenant_id = #{tenantId} AND task_id = #{taskId}
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<NotificationDeliveryAttemptEntity> findRecent(
            @Param("tenantId") String tenantId,
            @Param("taskId") String taskId,
            @Param("limit") int limit);
}
