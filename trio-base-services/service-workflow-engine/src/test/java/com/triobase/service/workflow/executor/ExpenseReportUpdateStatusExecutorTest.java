package com.triobase.service.workflow.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.workflow.config.WorkflowIntegrationProperties;
import com.triobase.service.workflow.entity.ExpenseReportActionLog;
import com.triobase.service.workflow.entity.ExpenseReportFixture;
import com.triobase.service.workflow.executor.expense.ExpenseReportUpdateStatusExecutor;
import com.triobase.service.workflow.mapper.ExpenseReportActionLogMapper;
import com.triobase.service.workflow.mapper.ExpenseReportFixtureMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseReportUpdateStatusExecutorTest {

    private final ExpenseReportActionLogMapper actionLogMapper = mock(ExpenseReportActionLogMapper.class);
    private final ExpenseReportFixtureMapper fixtureMapper = mock(ExpenseReportFixtureMapper.class);
    private final ExpenseReportUpdateStatusExecutor executor =
            new ExpenseReportUpdateStatusExecutor(
                    new ObjectMapper(),
                    actionLogMapper,
                    new WorkflowIntegrationProperties(),
                    fixtureMapper);

    @Test
    void updatesDraftToInApprovalWithTraceAndIdempotencyLog() {
        when(actionLogMapper.selectOne(any())).thenReturn(null);
        when(fixtureMapper.selectById("ER100")).thenReturn(report("ER100", "DRAFT"));

        BusinessActionResult result = executor.execute(new BusinessActionContext(
                "TENANT_A",
                "expense_report",
                "ER100",
                "updateStatus",
                Map.of("status", "IN_APPROVAL"),
                "idem-update-1",
                "trace-2",
                "user-2"));

        assertTrue(result.success());
        assertEquals("EXPENSE_REPORT_STATUS_UPDATED", result.resultCode());
        assertEquals("ER100", result.businessId());

        ArgumentCaptor<ExpenseReportFixture> reportCaptor =
                ArgumentCaptor.forClass(ExpenseReportFixture.class);
        verify(fixtureMapper).updateById(reportCaptor.capture());
        ExpenseReportFixture updated = reportCaptor.getValue();
        assertEquals("IN_APPROVAL", updated.getStatus());
        assertEquals("trace-2", updated.getTraceId());

        ArgumentCaptor<ExpenseReportActionLog> logCaptor =
                ArgumentCaptor.forClass(ExpenseReportActionLog.class);
        verify(actionLogMapper).insert(logCaptor.capture());
        assertEquals("expense_report.updateStatus", logCaptor.getValue().getExecutorKey());
        assertEquals("idem-update-1", logCaptor.getValue().getIdempotencyKey());
        verify(actionLogMapper).updateById(any(ExpenseReportActionLog.class));
    }

    @Test
    void rejectsInvalidStatusTransitionWithExplicitError() {
        when(actionLogMapper.selectOne(any())).thenReturn(null);
        when(fixtureMapper.selectById("ER100")).thenReturn(report("ER100", "APPROVED"));

        BusinessActionResult result = executor.execute(new BusinessActionContext(
                "TENANT_A",
                "expense_report",
                "ER100",
                "updateStatus",
                Map.of("status", "IN_APPROVAL"),
                "idem-update-2",
                "trace-3",
                "user-2"));

        assertFalse(result.success());
        assertEquals("INVALID_EXPENSE_STATUS_TRANSITION", result.resultCode());
        verify(fixtureMapper, never()).updateById(any(ExpenseReportFixture.class));
        verify(actionLogMapper).updateById(any(ExpenseReportActionLog.class));
    }

    private ExpenseReportFixture report(String id, String status) {
        ExpenseReportFixture report = new ExpenseReportFixture();
        report.setId(id);
        report.setTenantId("TENANT_A");
        report.setAmount(new BigDecimal("100.00"));
        report.setReason("Taxi");
        report.setStatus(status);
        return report;
    }
}
