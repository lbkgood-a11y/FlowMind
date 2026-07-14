package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_closure_outbox")
public class ClosureOutbox extends BaseEntity {
    private String closureId;
    private String effectId;
    private String eventType;
    private String payloadJson;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lockedAt;
    private LocalDateTime publishedAt;
    private String lastError;
    private String traceId;
}
