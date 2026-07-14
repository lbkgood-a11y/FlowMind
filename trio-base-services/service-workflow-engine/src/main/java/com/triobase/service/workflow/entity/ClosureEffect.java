package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_closure_effect")
public class ClosureEffect extends BaseEntity {
    private String closureId;
    private String effectKey;
    private String effectType;
    private String triggerOutcome;
    private String businessActionCode;
    private String executorKey;
    private String mode;
    private String status;
    private String idempotencyKey;
    private String requestJson;
    private String resultJson;
    private String failureCategory;
    private String lastError;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private String operatorId;
    private String traceId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
