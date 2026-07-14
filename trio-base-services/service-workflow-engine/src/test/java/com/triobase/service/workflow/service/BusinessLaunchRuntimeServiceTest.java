package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.StartProcessRequest;
import com.triobase.service.workflow.entity.ExpenseReportFixture;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.executor.BusinessActionContext;
import com.triobase.service.workflow.executor.BusinessActionExecutor;
import com.triobase.service.workflow.executor.BusinessActionResult;
import com.triobase.service.workflow.executor.ProcessExecutorRegistry;
import com.triobase.service.workflow.mapper.ExpenseReportFixtureMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessLaunchRuntimeServiceTest {

    private final ProcessExecutorRegistry executorRegistry = mock(ProcessExecutorRegistry.class);
    private final ExpenseReportFixtureMapper expenseReportFixtureMapper =
            mock(ExpenseReportFixtureMapper.class);
    private final BusinessLaunchRuntimeService service = new BusinessLaunchRuntimeService(
            new ObjectMapper(),
            executorRegistry,
            expenseReportFixtureMapper);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void existingDocumentLaunchChecksStatusAndRunsStartEffect() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of("/api/v1/process-instances/start:POST"),
                null,
                null,
                null));
        when(expenseReportFixtureMapper.selectById("ER100")).thenReturn(report("ER100", "DRAFT"));
        BusinessActionExecutor updateExecutor = mock(BusinessActionExecutor.class);
        when(executorRegistry.businessActionExecutor("expense_report.updateStatus"))
                .thenReturn(updateExecutor);
        when(updateExecutor.execute(any())).thenReturn(BusinessActionResult.succeeded(
                "EXPENSE_REPORT_STATUS_UPDATED",
                "ER100",
                Map.of("status", "IN_APPROVAL")));

        StartProcessRequest request = request("EXISTING_DOCUMENT", "ER100", "idem-1");

        BusinessLaunchRuntimeService.BusinessLaunchResult result =
                service.prepareLaunch(packageWithLaunchPlan(), request, "user-1");

        assertEquals("expense_report", result.businessType());
        assertEquals("ER100", result.businessId());
        assertEquals("EXISTING_DOCUMENT", result.launchMode());

        ArgumentCaptor<BusinessActionContext> contextCaptor =
                ArgumentCaptor.forClass(BusinessActionContext.class);
        verify(updateExecutor).execute(contextCaptor.capture());
        assertEquals("TENANT_A", contextCaptor.getValue().tenantId());
        assertEquals("idem-1:start:0:updateStatus", contextCaptor.getValue().idempotencyKey());
        assertEquals("IN_APPROVAL", contextCaptor.getValue().parameters().get("status"));
    }

    @Test
    void existingDocumentLaunchRejectsDisallowedStatusBeforeStartEffect() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of("/api/v1/process-instances/start:POST"),
                null,
                null,
                null));
        when(expenseReportFixtureMapper.selectById("ER100")).thenReturn(report("ER100", "APPROVED"));

        BizException exception = assertThrows(BizException.class,
                () -> service.prepareLaunch(packageWithLaunchPlan(),
                        request("EXISTING_DOCUMENT", "ER100", "idem-1"), "user-1"));

        assertEquals("BUSINESS_DOCUMENT_STATUS_NOT_ALLOWED", exception.getMessage());
    }

    @Test
    void launchRejectsMissingBusinessSubmitPermission() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of(),
                null,
                null,
                null));

        BizException exception = assertThrows(BizException.class,
                () -> service.prepareLaunch(packageWithLaunchPlan(),
                        request("EXISTING_DOCUMENT", "ER100", "idem-1"), "user-1"));

        assertEquals("BUSINESS_SUBMIT_PERMISSION_DENIED", exception.getMessage());
    }

    @Test
    void createAndLaunchUsesRegisteredCreateExecutorBeforeStartEffect() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of("/api/v1/process-instances/start:POST"),
                null,
                null,
                null));
        BusinessActionExecutor createExecutor = mock(BusinessActionExecutor.class);
        BusinessActionExecutor updateExecutor = mock(BusinessActionExecutor.class);
        when(executorRegistry.businessActionExecutor("expense_report.createDocument"))
                .thenReturn(createExecutor);
        when(executorRegistry.businessActionExecutor("expense_report.updateStatus"))
                .thenReturn(updateExecutor);
        when(createExecutor.execute(any())).thenReturn(BusinessActionResult.succeeded(
                "EXPENSE_REPORT_CREATED",
                "ER200",
                Map.of("status", "DRAFT")));
        when(updateExecutor.execute(any())).thenReturn(BusinessActionResult.succeeded(
                "EXPENSE_REPORT_STATUS_UPDATED",
                "ER200",
                Map.of("status", "IN_APPROVAL")));

        StartProcessRequest request = request("CREATE_AND_LAUNCH", null, "idem-2");
        request.setFormData(Map.of("amount", new BigDecimal("30.00"), "reason", "Taxi"));

        BusinessLaunchRuntimeService.BusinessLaunchResult result =
                service.prepareLaunch(packageWithLaunchPlan(), request, "user-1");

        assertEquals("ER200", result.businessId());
        assertEquals("CREATE_AND_LAUNCH", result.launchMode());
        verify(createExecutor).execute(any(BusinessActionContext.class));
        verify(updateExecutor).execute(any(BusinessActionContext.class));
    }

    @Test
    void createAndLaunchFailsWhenCreateExecutorFails() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of("/api/v1/process-instances/start:POST"),
                null,
                null,
                null));
        BusinessActionExecutor createExecutor = mock(BusinessActionExecutor.class);
        when(executorRegistry.businessActionExecutor("expense_report.createDocument"))
                .thenReturn(createExecutor);
        when(createExecutor.execute(any())).thenReturn(BusinessActionResult.failed(
                "EXPENSE_AMOUNT_REQUIRED",
                "amount missing"));

        StartProcessRequest request = request("CREATE_AND_LAUNCH", null, "idem-3");
        request.setFormData(Map.of("reason", "Taxi"));

        BizException exception = assertThrows(BizException.class,
                () -> service.prepareLaunch(packageWithLaunchPlan(), request, "user-1"));

        assertEquals("BUSINESS_DOCUMENT_CREATE_FAILED:EXPENSE_AMOUNT_REQUIRED",
                exception.getMessage());
    }

    @Test
    void launchFailsWhenHardStartEffectFails() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of("/api/v1/process-instances/start:POST"),
                null,
                null,
                null));
        when(expenseReportFixtureMapper.selectById("ER100")).thenReturn(report("ER100", "DRAFT"));
        BusinessActionExecutor updateExecutor = mock(BusinessActionExecutor.class);
        when(executorRegistry.businessActionExecutor("expense_report.updateStatus"))
                .thenReturn(updateExecutor);
        when(updateExecutor.execute(any())).thenReturn(BusinessActionResult.failed(
                "INVALID_EXPENSE_STATUS_TRANSITION",
                "bad transition"));

        BizException exception = assertThrows(BizException.class,
                () -> service.prepareLaunch(packageWithLaunchPlan(),
                        request("EXISTING_DOCUMENT", "ER100", "idem-4"), "user-1"));

        assertEquals("START_EFFECT_FAILED:INVALID_EXPENSE_STATUS_TRANSITION",
                exception.getMessage());
    }

    private StartProcessRequest request(String launchMode, String businessId, String idempotencyKey) {
        StartProcessRequest request = new StartProcessRequest();
        request.setProcessKey("expense_report");
        request.setLaunchMode(launchMode);
        request.setBusinessType("expense_report");
        request.setBusinessId(businessId);
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private ProcessPackage packageWithLaunchPlan() {
        ProcessPackage pkg = new ProcessPackage();
        pkg.setId("PKG001");
        pkg.setProcessKey("expense_report");
        pkg.setBusinessBindingSnapshot("""
                {"businessObject":{"typeCode":"expense_report","displayName":"报销单"}}
                """);
        pkg.setLaunchPlanJson("""
                {
                  "policy": {"modes": ["EXISTING_DOCUMENT", "CREATE_AND_LAUNCH"]},
                  "submitPermission": {"permissionCode": "/api/v1/process-instances/start:POST"},
                  "allowedStatuses": [{"statusCode": "DRAFT"}, {"statusCode": "REJECTED"}],
                  "createAction": {
                    "actionCode": "createDocument",
                    "executorKey": "expense_report.createDocument"
                  },
                  "startEffects": [{
                    "mode": "HARD",
                    "params": {"status": "IN_APPROVAL"},
                    "action": {
                      "actionCode": "updateStatus",
                      "executorKey": "expense_report.updateStatus"
                    }
                  }]
                }
                """);
        return pkg;
    }

    private ExpenseReportFixture report(String id, String status) {
        ExpenseReportFixture report = new ExpenseReportFixture();
        report.setId(id);
        report.setAmount(new BigDecimal("100.00"));
        report.setReason("Taxi");
        report.setStatus(status);
        return report;
    }
}
