package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_business_event")
public class ProcessBusinessEvent extends BaseEntity {
    private String eventKey;
    private String eventType;
    private String tenantId;
    private String processInstanceId;
    private String processOutcomeId;
    private String processClosureId;
    private String processKey;
    private Integer processVersion;
    private String businessType;
    private String businessId;
    private String outcomeStatus;
    private String closureStatus;
    private String payloadJson;
    private String traceId;
    private String status;
}
