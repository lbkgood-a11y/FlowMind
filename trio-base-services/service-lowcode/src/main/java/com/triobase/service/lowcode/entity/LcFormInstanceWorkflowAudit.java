package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_form_instance_workflow_audit")
public class LcFormInstanceWorkflowAudit extends BaseEntity {
    private String tenantId;
    private String formInstanceId;
    private String formKey;
    private String processKey;
    private String processInstanceId;
    private String previousWorkflowStatus;
    private String workflowStatus;
    private String changeType;
    private String traceId;
}
