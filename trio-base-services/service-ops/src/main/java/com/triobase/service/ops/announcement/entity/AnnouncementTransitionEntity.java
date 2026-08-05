package com.triobase.service.ops.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_announcement_transition")
public class AnnouncementTransitionEntity {
    @TableId
    private String id;
    private String tenantId;
    private String versionId;
    private String fromState;
    private String toState;
    private String transitionType;
    private String reason;
    private String actorId;
    private String actorName;
    private String traceId;
    private LocalDateTime occurredAt;
}
