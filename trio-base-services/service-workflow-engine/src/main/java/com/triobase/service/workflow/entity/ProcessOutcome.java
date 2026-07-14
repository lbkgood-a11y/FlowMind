package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_outcome")
public class ProcessOutcome extends BaseEntity {
    private String processInstanceId;
    private Integer outcomeVersion;
    private String processPackageId;
    private String processKey;
    private Integer processVersion;
    private String businessType;
    private String businessId;
    private String outcomeStatus;
    private String resultCode;
    private String reason;
    private String tenantId;
    private String initiatorId;
    private String lastOperatorId;
    private String traceId;
    private String payloadJson;
}
