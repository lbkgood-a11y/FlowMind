package com.triobase.service.workflow.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.workflow.config.WorkflowIntegrationProperties;
import com.triobase.service.workflow.entity.ExpenseReportActionLog;
import com.triobase.service.workflow.entity.ExpenseReportFixture;
import com.triobase.service.workflow.executor.expense.ExpenseReportCreateDocumentExecutor;
import com.triobase.service.workflow.mapper.ExpenseReportActionLogMapper;
import com.triobase.service.workflow.mapper.ExpenseReportFixtureMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseReportCreateDocumentExecutorTest {

    private final ExpenseReportActionLogMapper actionLogMapper = mock(ExpenseReportActionLogMapper.class);
    private final ExpenseReportFixtureMapper fixtureMapper = mock(ExpenseReportFixtureMapper.class);
    private final ExpenseReportCreateDocumentExecutor executor =
            new ExpenseReportCreateDocumentExecutor(
                    new ObjectMapper(),
                    actionLogMapper,
                    new WorkflowIntegrationProperties(),
                    fixtureMapper);

    @Test
    void createsDraftExpenseReportWithTraceAndIdempotencyLog() {
        when(actionLogMapper.selectOne(any())).thenReturn(null);
        when(fixtureMapper.selectById("ER100")).thenReturn(null);

        BusinessActionResult result = executor.execute(new BusinessActionContext(
                "TENANT_A",
                "expense_report",
                null,
                "createDocument",
                Map.of("businessId", "ER100", "amount", new BigDecimal("88.50"),
                        "reason", "Taxi", "dept", "sales"),
                "idem-create-1",
                "trace-1",
                "user-1"));

        assertTrue(result.success());
        assertEquals("EXPENSE_REPORT_CREATED", result.resultCode());
        assertEquals("ER100", result.businessId());

        ArgumentCaptor<ExpenseReportFixture> reportCaptor =
                ArgumentCaptor.forClass(ExpenseReportFixture.class);
        verify(fixtureMapper).insert(reportCaptor.capture());
        ExpenseReportFixture report = reportCaptor.getValue();
        assertEquals("ER100", report.getId());
        assertEquals("TENANT_A", report.getTenantId());
        assertEquals("DRAFT", report.getStatus());
        assertEquals("trace-1", report.getTraceId());

        ArgumentCaptor<ExpenseReportActionLog> logCaptor =
                ArgumentCaptor.forClass(ExpenseReportActionLog.class);
        verify(actionLogMapper).insert(logCaptor.capture());
        assertEquals("expense_report.createDocument", logCaptor.getValue().getExecutorKey());
        assertEquals("idem-create-1", logCaptor.getValue().getIdempotencyKey());
        verify(actionLogMapper).updateById(any(ExpenseReportActionLog.class));
    }

    @Test
    void replaysExistingSuccessfulCreateResult() {
        ExpenseReportActionLog existing = new ExpenseReportActionLog();
        existing.setStatus("SUCCEEDED");
        existing.setExecutorKey("expense_report.createDocument");
        existing.setIdempotencyKey("idem-create-1");
        existing.setBusinessId("ER100");
        existing.setResultCode("EXPENSE_REPORT_CREATED");
        existing.setResultJson("{\"businessId\":\"ER100\"}");
        existing.setTraceId("trace-1");
        when(actionLogMapper.selectOne(any())).thenReturn(existing);

        BusinessActionResult result = executor.execute(new BusinessActionContext(
                "TENANT_A",
                "expense_report",
                null,
                "createDocument",
                Map.of("businessId", "ER100", "amount", 88.5, "reason", "Taxi"),
                "idem-create-1",
                "trace-1",
                "user-1"));

        assertTrue(result.success());
        assertEquals("IDEMPOTENT_REPLAY", result.message());
        assertEquals("ER100", result.businessId());
        verify(fixtureMapper, never()).insert(any(ExpenseReportFixture.class));
    }
}
