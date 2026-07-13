package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_node_record")
public class NodeRecord extends BaseEntity {
    private String processInstanceId;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String status;             // PENDING / ACTIVE / COMPLETED / FAILED / SKIPPED
    private String assigneeSnapshot;   // 参与者快照 JSON
    private String result;             // 执行结果 JSON
    private LocalDateTime enteredAt;
    private LocalDateTime exitedAt;
}
