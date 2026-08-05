package com.triobase.service.ops.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.notification.entity.NotificationResolutionCheckpointEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationResolutionCheckpointMapper
        extends BaseMapper<NotificationResolutionCheckpointEntity> {

    @Insert("""
            INSERT INTO ops_notification_resolution_checkpoint
                (id, tenant_id, task_id, resolver_key, resolver_version, cursor_value,
                 resolution_state, resolved_count, updated_at)
            VALUES
                (#{id}, #{tenantId}, #{taskId}, #{resolverKey}, #{resolverVersion}, #{cursorValue},
                 #{resolutionState}, #{resolvedCount}, #{updatedAt})
            ON CONFLICT (tenant_id, task_id, resolver_key) DO UPDATE SET
                resolver_version = EXCLUDED.resolver_version,
                cursor_value = EXCLUDED.cursor_value,
                resolution_state = EXCLUDED.resolution_state,
                resolved_count = ops_notification_resolution_checkpoint.resolved_count
                    + EXCLUDED.resolved_count,
                updated_at = EXCLUDED.updated_at
            """)
    int upsert(NotificationResolutionCheckpointEntity checkpoint);
}
