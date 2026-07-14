package com.triobase.service.workflow.executor.expense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.workflow.config.WorkflowIntegrationProperties;
import com.triobase.service.workflow.entity.ExpenseReportActionLog;
import com.triobase.service.workflow.entity.ExpenseReportFixture;
import com.triobase.service.workflow.executor.BusinessActionContext;
import com.triobase.service.workflow.executor.BusinessActionExecutor;
import com.triobase.service.workflow.executor.BusinessActionResult;
import com.triobase.service.workflow.mapper.ExpenseReportActionLogMapper;
import com.triobase.service.workflow.mapper.ExpenseReportFixtureMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class ExpenseReportCreateDocumentExecutor extends ExpenseReportExecutorSupport
        implements BusinessActionExecutor {

    public static final String EXECUTOR_KEY = "expense_report.createDocument";
    private static final String ACTION_CODE = "createDocument";

    private final ExpenseReportFixtureMapper fixtureMapper;

    public ExpenseReportCreateDocumentExecutor(ObjectMapper objectMapper,
                                               ExpenseReportActionLogMapper actionLogMapper,
                                               WorkflowIntegrationProperties properties,
                                               ExpenseReportFixtureMapper fixtureMapper) {
        super(objectMapper, actionLogMapper, properties);
        this.fixtureMapper = fixtureMapper;
    }

    @Override
    public String executorKey() {
        return EXECUTOR_KEY;
    }

    @Override
    @Transactional
    public BusinessActionResult execute(BusinessActionContext context) {
        return executeOnce(EXECUTOR_KEY, ACTION_CODE, context, log -> createDocument(context, log));
    }

    private BusinessActionResult createDocument(BusinessActionContext context,
                                                ExpenseReportActionLog log) {
        Map<String, Object> parameters = parameters(context);
        BigDecimal amount = decimalParameter(parameters, "amount");
        String reason = stringParameter(parameters, "reason");
        if (amount == null || amount.signum() <= 0) {
            return BusinessActionResult.failed("EXPENSE_AMOUNT_REQUIRED",
                    "Expense amount must be greater than zero");
        }
        if (!StringUtils.hasText(reason)) {
            return BusinessActionResult.failed("EXPENSE_REASON_REQUIRED",
                    "Expense reason is required");
        }

        String businessId = stringParameter(parameters, "businessId");
        if (!StringUtils.hasText(businessId)) {
            businessId = UlidGenerator.nextUlid();
        }
        if (fixtureMapper.selectById(businessId) != null) {
            return BusinessActionResult.failed("EXPENSE_REPORT_ALREADY_EXISTS",
                    "Expense report already exists: " + businessId);
        }

        ExpenseReportFixture report = new ExpenseReportFixture();
        report.setId(businessId);
        report.setTenantId(tenantId(context));
        report.setApplicantId(trimToNull(context.operatorId()));
        report.setAmount(amount);
        report.setReason(reason);
        report.setDept(stringParameter(parameters, "dept"));
        report.setStatus("DRAFT");
        report.setTraceId(trimToNull(context.traceId()));
        report.setPayloadJson(writeJson(parameters));
        fixtureMapper.insert(report);

        Map<String, Object> data = resultData();
        data.put("businessId", businessId);
        data.put("status", "DRAFT");
        data.put("traceId", context.traceId());
        log.setBusinessId(businessId);
        return BusinessActionResult.succeeded("EXPENSE_REPORT_CREATED", businessId, data);
    }
}
