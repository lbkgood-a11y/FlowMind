package com.triobase.service.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_job_execution_log")
public class OpsJobExecutionLog extends BaseEntity {
    private String tenantId;
    private String jobId;
    private String jobCode;
    private String triggerType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMs;
    private String resultSummary;
    private String errorMessage;
    private String runInstance;
    private String triggeredBy;
}
