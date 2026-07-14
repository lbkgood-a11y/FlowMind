package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_instance")
public class ProcessInstance extends BaseEntity {
    private String processPackageId;
    private String processKey;
    private String processName;
    private Integer version;
    private String title;
    private String status;          // RUNNING / SUSPENDED / COMPLETED / TERMINATED
    private String formData;        // 提交的表单数据 JSON
    private String tenantId;
    private String businessType;
    private String businessId;
    private String launchMode;
    private String launchIdempotencyKey;
    private String initiatorId;
    private String initiatorName;
    private String currentNodeId;
    private String workflowId;      // Temporal Workflow ID
    private String runId;           // Temporal Run ID
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
