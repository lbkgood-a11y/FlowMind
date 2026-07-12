package com.triobase.service.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_announcement")
public class OpsAnnouncement extends BaseEntity {
    private String tenantId;
    private String title;
    private String content;
    private String priority;
    private String status;
    private String targetType;
    private String targetOrgIds;
    private String targetUserIds;
    private LocalDateTime publishAt;
    private LocalDateTime unpublishAt;
}
