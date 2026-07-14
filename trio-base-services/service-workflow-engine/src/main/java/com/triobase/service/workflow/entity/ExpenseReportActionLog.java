package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_expense_report_action_log")
public class ExpenseReportActionLog extends BaseEntity {
    private String tenantId;
    private String executorKey;
    private String idempotencyKey;
    private String businessId;
    private String actionCode;
    private String status;
    private String resultCode;
    private String requestJson;
    private String resultJson;
    private String lastError;
    private String traceId;
}
