package com.triobase.service.ops.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_announcement_receipt")
public class AnnouncementReceiptEntity {
    @TableId
    private String id;
    private String tenantId;
    private String versionId;
    private String recipientUserId;
    private LocalDateTime readAt;
    private LocalDateTime confirmedAt;
    private String confirmationStatementHash;
    private String confirmationTraceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
