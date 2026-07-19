package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_closure")
public class ProcessClosure extends BaseEntity {
    private String outcomeId;
    private String processInstanceId;
    private String businessType;
    private String businessId;
    private String closureStatus;
    private String hardFailurePolicy;
    private String traceId;
    private String actionId;
    private String actionType;
    private String actionSource;
    private String actionActorType;
    private String actionActorId;
    private String actionActorName;
    private String actionCorrelationId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
