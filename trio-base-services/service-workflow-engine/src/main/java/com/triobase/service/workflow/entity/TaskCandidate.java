package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_task_candidate")
public class TaskCandidate extends BaseEntity {
    private String taskId;
    private String userId;
    private String username;
    private String sourceType;
    private String sourceRef;
}
