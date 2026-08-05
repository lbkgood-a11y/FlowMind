package com.triobase.service.ops.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_announcement_audience_rule")
public class AnnouncementAudienceRuleEntity {
    @TableId
    private String id;
    private String tenantId;
    private String versionId;
    private String selectorType;
    private String subjectId;
    private Short includeDescendants;
    private String resolverKey;
    private String resolverVersion;
    private String createdBy;
    private LocalDateTime createdAt;
}
