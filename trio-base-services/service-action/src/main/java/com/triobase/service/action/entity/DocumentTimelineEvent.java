package com.triobase.service.action.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("act_document_timeline_event")
public class DocumentTimelineEvent extends BaseEntity {
    private String eventSource;
    private String tenantId;
    private String targetType;
    private String targetId;
    private String eventType;
    private String displayName;
    private String actorId;
    private String actorName;
    private String actionId;
    private String actionType;
    private String actionStatus;
    private String ownerService;
    private String ownerExecutionRef;
    private String traceId;
    private String correlationId;
    private String summaryJson;
    private Boolean redacted;
    private LocalDateTime occurredAt;
}
