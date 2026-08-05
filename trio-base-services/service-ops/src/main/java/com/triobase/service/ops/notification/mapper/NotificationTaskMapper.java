package com.triobase.service.ops.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.notification.entity.NotificationTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface NotificationTaskMapper extends BaseMapper<NotificationTaskEntity> {

    @Select("SELECT * FROM ops_notification_task WHERE tenant_id = #{tenantId} AND id = #{taskId}")
    NotificationTaskEntity findOwned(@Param("tenantId") String tenantId,
                                     @Param("taskId") String taskId);

    @Select("""
            SELECT * FROM ops_notification_task
            WHERE tenant_id = #{tenantId} AND producer = #{producer}
              AND idempotency_key = #{idempotencyKey}
            """)
    NotificationTaskEntity findByIdempotency(@Param("tenantId") String tenantId,
                                              @Param("producer") String producer,
                                              @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE ops_notification_task
            SET task_state = #{state}, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND id = #{taskId}
              AND task_state IN ('ACCEPTED','RESOLVING','DELIVERING','PARTIALLY_DELIVERED')
            """)
    int advanceState(@Param("tenantId") String tenantId,
                     @Param("taskId") String taskId,
                     @Param("state") String state);

    @Update("""
            UPDATE ops_notification_task
            SET resolved_count = resolved_count + #{inserted},
                delivered_count = delivered_count + #{inserted},
                task_state = 'DELIVERING', updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND id = #{taskId}
              AND task_state IN ('RESOLVING','DELIVERING')
            """)
    int addDelivered(@Param("tenantId") String tenantId,
                     @Param("taskId") String taskId,
                     @Param("inserted") long inserted);

    @Update("""
            UPDATE ops_notification_task
            SET task_state = 'CANCELLED', cancellation_reason = #{reason}, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND id = #{taskId}
              AND task_state IN ('ACCEPTED','RESOLVING','DELIVERING','PARTIALLY_DELIVERED')
            """)
    int cancelUndelivered(@Param("tenantId") String tenantId,
                          @Param("taskId") String taskId,
                          @Param("reason") String reason);

    @Update("""
            UPDATE ops_notification_task
            SET cancellation_reason = #{reason}, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND id = #{taskId}
            """)
    int recordWithdrawal(@Param("tenantId") String tenantId,
                         @Param("taskId") String taskId,
                         @Param("reason") String reason);

    @Update("""
            UPDATE ops_notification_task
            SET task_state = #{state}, failed_count = failed_count + 1,
                next_attempt_at = #{nextAttemptAt}, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND id = #{taskId}
              AND task_state NOT IN ('DELIVERED','CANCELLED','EXPIRED')
            """)
    int recordFailure(@Param("tenantId") String tenantId,
                      @Param("taskId") String taskId,
                      @Param("state") String state,
                      @Param("nextAttemptAt") java.time.LocalDateTime nextAttemptAt);

    @Update("""
            UPDATE ops_notification_task
            SET task_state = 'ACCEPTED', next_attempt_at = NULL,
                cancellation_reason = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND id = #{taskId}
              AND task_state IN ('FAILED','PARTIALLY_DELIVERED')
            """)
    int resetForManualRetry(@Param("tenantId") String tenantId,
                            @Param("taskId") String taskId);

    @Select("""
            SELECT COUNT(*) FROM ops_notification_task
            WHERE task_state IN ('ACCEPTED','RESOLVING','DELIVERING','PARTIALLY_DELIVERED')
            """)
    long countBacklog();

    @Select("""
            SELECT MIN(created_at) FROM ops_notification_task
            WHERE task_state IN ('ACCEPTED','RESOLVING','DELIVERING','PARTIALLY_DELIVERED')
            """)
    LocalDateTime oldestBacklogCreatedAt();
}
