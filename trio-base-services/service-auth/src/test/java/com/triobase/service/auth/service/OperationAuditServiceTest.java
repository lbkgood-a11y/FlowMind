package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.auth.entity.SysOperationAuditLog;
import com.triobase.service.auth.mapper.OperationAuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationAuditServiceTest {

    @Mock
    private OperationAuditLogMapper operationAuditLogMapper;

    @InjectMocks
    private OperationAuditService operationAuditService;

    @Test
    void record_shouldFillDefaultsAndInsert() {
        SysOperationAuditLog log = new SysOperationAuditLog();
        log.setRequestPath("/api/v1/users");
        log.setActionId("act-1");
        log.setActionType("process.task.approve");

        operationAuditService.record(log);

        ArgumentCaptor<SysOperationAuditLog> captor = ArgumentCaptor.forClass(SysOperationAuditLog.class);
        verify(operationAuditLogMapper).insert(captor.capture());
        SysOperationAuditLog saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals("default", saved.getTenantId());
        assertEquals("SUCCESS", saved.getResultStatus());
        assertEquals("act-1", saved.getActionId());
        assertEquals("process.task.approve", saved.getActionType());
        assertNotNull(saved.getOperatedAt());
    }

    @Test
    void record_shouldPersistActionLinkedFailureWithRedactedSummary() {
        SysOperationAuditLog log = new SysOperationAuditLog();
        log.setRequestPath("/api/v1/actions");
        log.setResultStatus("FAILURE");
        log.setActionId("act-failed");
        log.setActionType("integration.orchestration.start");
        log.setActionSource("GUI");
        log.setActionStatus("FAILED");
        log.setActionTargetType("INTEGRATION_ROUTE");
        log.setActionTargetId("orders.submit");
        log.setActionCorrelationId("corr-1");
        log.setActionIdempotencyKey("idem-1");
        log.setActionSummary("{\"credential\":\"***REDACTED***\"}");

        operationAuditService.record(log);

        ArgumentCaptor<SysOperationAuditLog> captor = ArgumentCaptor.forClass(SysOperationAuditLog.class);
        verify(operationAuditLogMapper).insert(captor.capture());
        SysOperationAuditLog saved = captor.getValue();
        assertEquals("act-failed", saved.getActionId());
        assertEquals("FAILED", saved.getActionStatus());
        assertEquals("{\"credential\":\"***REDACTED***\"}", saved.getActionSummary());
    }

    @Test
    void record_shouldIgnoreLogWithoutRequestPath() {
        operationAuditService.record(new SysOperationAuditLog());

        verify(operationAuditLogMapper, never()).insert(any(SysOperationAuditLog.class));
    }

    @Test
    void page_shouldReturnMapperPageResult() {
        SysOperationAuditLog log = new SysOperationAuditLog();
        log.setId("A001");
        log.setRequestPath("/api/v1/users");
        Page<SysOperationAuditLog> page = new Page<>(1, 20);
        page.setRecords(List.of(log));
        page.setTotal(1);
        when(operationAuditLogMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<SysOperationAuditLog> result = operationAuditService.page(
                1, 20, "admin", null, "/api/v1/users", "SUCCESS",
                "act-1", "process.task.approve", "GUI", "SUCCEEDED",
                "PROCESS_TASK", "task-1", "corr-1", "idem-1", null, null);

        assertEquals(1, result.getTotal());
        assertEquals("A001", result.getRecords().get(0).getId());
    }

    @Test
    void detail_shouldReturnHttpOnlyOperationDetail() {
        SysOperationAuditLog log = new SysOperationAuditLog();
        log.setId("A001");
        log.setRequestPath("/api/v1/users");
        log.setRequestSummary("{\"username\":\"admin\"}");
        log.setResponseSummary("{\"success\":true}");
        log.setTraceId("trace-1");
        when(operationAuditLogMapper.selectById("A001")).thenReturn(log);

        SysOperationAuditLog result = operationAuditService.detail("A001");

        assertEquals("/api/v1/users", result.getRequestPath());
        assertEquals("{\"username\":\"admin\"}", result.getRequestSummary());
        assertEquals("{\"success\":true}", result.getResponseSummary());
        assertEquals("trace-1", result.getTraceId());
    }

    @Test
    void detail_shouldReturnActionLinkedRedactedDetail() {
        SysOperationAuditLog log = new SysOperationAuditLog();
        log.setId("A002");
        log.setRequestPath("/api/v1/actions");
        log.setActionId("act-2");
        log.setActionType("process.task.approve");
        log.setActionSource("GUI");
        log.setActionStatus("SUCCEEDED");
        log.setActionTargetType("PROCESS_TASK");
        log.setActionTargetId("task-2");
        log.setActionCorrelationId("corr-2");
        log.setActionIdempotencyKey("idem-2");
        log.setActionSummary("{\"comment\":\"***REDACTED***\"}");
        when(operationAuditLogMapper.selectById("A002")).thenReturn(log);

        SysOperationAuditLog result = operationAuditService.detail("A002");

        assertEquals("act-2", result.getActionId());
        assertEquals("process.task.approve", result.getActionType());
        assertEquals("SUCCEEDED", result.getActionStatus());
        assertEquals("{\"comment\":\"***REDACTED***\"}", result.getActionSummary());
    }

    @Test
    void detail_shouldThrow_whenLogMissing() {
        when(operationAuditLogMapper.selectById("A404")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> operationAuditService.detail("A404"));

        assertEquals(40471, ex.getCode());
    }
}
