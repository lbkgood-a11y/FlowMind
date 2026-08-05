package com.triobase.service.ops.announcement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.announcement.entity.AnnouncementSnapshotCheckpointEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnnouncementSnapshotCheckpointMapper
        extends BaseMapper<AnnouncementSnapshotCheckpointEntity> {

    @Insert("""
            INSERT INTO ops_announcement_snapshot_checkpoint(
                id, tenant_id, version_id, rule_id, cursor_value, checkpoint_state,
                resolved_count, updated_at)
            VALUES(#{id}, #{tenantId}, #{versionId}, #{ruleId}, #{cursorValue},
                   #{checkpointState}, #{resolvedCount}, #{updatedAt})
            ON CONFLICT (tenant_id, version_id, rule_id)
            DO UPDATE SET cursor_value = EXCLUDED.cursor_value,
                          checkpoint_state = EXCLUDED.checkpoint_state,
                          resolved_count = ops_announcement_snapshot_checkpoint.resolved_count
                                           + EXCLUDED.resolved_count,
                          updated_at = EXCLUDED.updated_at
            """)
    int upsert(AnnouncementSnapshotCheckpointEntity entity);
}
