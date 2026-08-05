package com.triobase.service.ops.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_announcement_version")
public class AnnouncementVersionEntity extends BaseEntity {
    private String tenantId;
    private String announcementId;
    private Integer versionNo;
    private String title;
    private String content;
    private String priority;
    private String lifecycleState;
    private String audienceMode;
    private Short confirmationRequired;
    private String confirmationStatement;
    private String confirmationStatementHash;
    private LocalDateTime confirmationDeadline;
    private LocalDateTime scheduledPublishAt;
    private LocalDateTime publishedAt;
    private LocalDateTime effectiveUntil;
    private LocalDateTime pinFrom;
    private LocalDateTime pinUntil;
    private String predecessorVersionId;
    private String withdrawalReason;
    private LocalDateTime withdrawnAt;
}
