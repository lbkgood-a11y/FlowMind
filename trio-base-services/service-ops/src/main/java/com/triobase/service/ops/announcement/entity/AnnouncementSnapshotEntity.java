package com.triobase.service.ops.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_announcement_recipient_snapshot")
public class AnnouncementSnapshotEntity {
    @TableId
    private String id;
    private String tenantId;
    private String versionId;
    private String recipientUserId;
    private String resolverKey;
    private String resolverVersion;
    private LocalDateTime resolvedAt;
}
