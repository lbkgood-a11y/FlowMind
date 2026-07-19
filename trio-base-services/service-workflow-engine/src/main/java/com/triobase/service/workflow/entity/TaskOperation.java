package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_task_operation")
public class TaskOperation extends BaseEntity {
    private String operationId;
    private String processInstanceId;
    private String sourceTaskId;
    private String targetTaskId;
    private String action;
    private String operatorId;
    private String operatorName;
    private String targetUserId;
    private String targetUserName;
    private String targetNodeId;
    private String comment;
    private String status;
    private String traceId;
    private String resultJson;
    private String actionId;
    private String actionType;
    private String actionSource;
    private String actionActorType;
    private String actionActorId;
    private String actionActorName;
    private String actionCorrelationId;
}
