package com.triobase.service.ops.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_announcement_snapshot_checkpoint")
public class AnnouncementSnapshotCheckpointEntity {
    @TableId
    private String id;
    private String tenantId;
    private String versionId;
    private String ruleId;
    private String cursorValue;
    private String checkpointState;
    private Long resolvedCount;
    private LocalDateTime updatedAt;
}
