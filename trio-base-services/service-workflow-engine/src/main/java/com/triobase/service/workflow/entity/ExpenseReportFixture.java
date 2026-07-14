package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_expense_report_fixture")
public class ExpenseReportFixture extends BaseEntity {
    private String tenantId;
    private String applicantId;
    private BigDecimal amount;
    private String reason;
    private String dept;
    private String status;
    private String traceId;
    private String payloadJson;
}
