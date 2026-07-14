package com.triobase.service.workflow.executor.expense;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;
import java.util.Set;

@Component
public class ExpenseReportUpdateStatusExecutor extends ExpenseReportExecutorSupport
        implements BusinessActionExecutor {

    public static final String EXECUTOR_KEY = "expense_report.updateStatus";
    private static final String ACTION_CODE = "updateStatus";
    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "IN_APPROVAL", "APPROVED", "REJECTED");

    private final ExpenseReportFixtureMapper fixtureMapper;

    public ExpenseReportUpdateStatusExecutor(ObjectMapper objectMapper,
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
        return executeOnce(EXECUTOR_KEY, ACTION_CODE, context, log -> updateStatus(context, log));
    }

    private BusinessActionResult updateStatus(BusinessActionContext context,
                                              ExpenseReportActionLog log) {
        if (!StringUtils.hasText(context.businessId())) {
            return BusinessActionResult.failed("BUSINESS_ID_REQUIRED",
                    "Expense report status update requires businessId");
        }

        String targetStatus = stringParameter(parameters(context), "status");
        if (!StringUtils.hasText(targetStatus)) {
            targetStatus = stringParameter(parameters(context), "targetStatus");
        }
        if (!STATUSES.contains(targetStatus)) {
            return BusinessActionResult.failed("TARGET_STATUS_INVALID",
                    "Expense report target status is invalid: " + targetStatus);
        }

        ExpenseReportFixture report = fixtureMapper.selectById(context.businessId().trim());
        if (report == null) {
            return BusinessActionResult.failed("EXPENSE_REPORT_NOT_FOUND",
                    "Expense report not found: " + context.businessId());
        }

        String sourceStatus = report.getStatus();
        if (targetStatus.equals(sourceStatus)) {
            return success(context, log, sourceStatus, targetStatus,
                    "EXPENSE_REPORT_STATUS_UNCHANGED");
        }
        if (!canTransition(sourceStatus, targetStatus)) {
            return BusinessActionResult.failed("INVALID_EXPENSE_STATUS_TRANSITION",
                    "Cannot update expense report from " + sourceStatus + " to " + targetStatus);
        }

        report.setStatus(targetStatus);
        report.setTraceId(trimToNull(context.traceId()));
        report.setUpdatedBy(trimToNull(context.operatorId()));
        fixtureMapper.updateById(report);
        return success(context, log, sourceStatus, targetStatus, "EXPENSE_REPORT_STATUS_UPDATED");
    }

    private boolean canTransition(String sourceStatus, String targetStatus) {
        return ("DRAFT".equals(sourceStatus) || "REJECTED".equals(sourceStatus))
                && "IN_APPROVAL".equals(targetStatus)
                || "IN_APPROVAL".equals(sourceStatus)
                && ("APPROVED".equals(targetStatus) || "REJECTED".equals(targetStatus));
    }

    private BusinessActionResult success(BusinessActionContext context,
                                         ExpenseReportActionLog log,
                                         String sourceStatus,
                                         String targetStatus,
                                         String resultCode) {
        Map<String, Object> data = resultData();
        data.put("businessId", context.businessId());
        data.put("fromStatus", sourceStatus);
        data.put("toStatus", targetStatus);
        data.put("traceId", context.traceId());
        log.setBusinessId(context.businessId());
        return BusinessActionResult.succeeded(resultCode, context.businessId(), data);
    }
}
