package com.triobase.service.ops.announcement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.announcement.entity.AnnouncementSnapshotEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnnouncementSnapshotMapper extends BaseMapper<AnnouncementSnapshotEntity> {

    @Insert("""
            INSERT INTO ops_announcement_recipient_snapshot(
                id, tenant_id, version_id, recipient_user_id, resolver_key, resolver_version, resolved_at)
            VALUES(#{id}, #{tenantId}, #{versionId}, #{recipientUserId},
                   #{resolverKey}, #{resolverVersion}, #{resolvedAt})
            ON CONFLICT (tenant_id, version_id, recipient_user_id) DO NOTHING
            """)
    int insertIgnore(AnnouncementSnapshotEntity entity);
}
