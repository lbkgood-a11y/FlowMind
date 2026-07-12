package com.triobase.service.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_announcement_read")
public class OpsAnnouncementRead extends BaseEntity {
    private String tenantId;
    private String announcementId;
    private String userId;
    private LocalDateTime readAt;
}
