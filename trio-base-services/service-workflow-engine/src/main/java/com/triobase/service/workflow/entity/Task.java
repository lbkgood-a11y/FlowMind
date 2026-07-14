package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_task")
public class Task extends BaseEntity {
    private String processInstanceId;
    private String processKey;
    private String processName;
    private String nodeId;
    private String nodeName;
    private String nodeType;       // APPROVAL / COUNTERSIGN / NOTIFY
    private Integer nodeVisitNo;
    private String title;
    private String status;         // PENDING / APPROVED / REJECTED / TRANSFERRED / CANCELLED
    private String assigneeId;     // 固化参与者快照
    private String assigneeName;
    private String assigneeType;   // ROLE / DEPT / USER / SYSTEM
    private String sourceTaskId;
    private String rootTaskId;
    private String comment;
    private LocalDateTime claimedAt;
    private LocalDateTime completedAt;
}
