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

        operationAuditService.record(log);

        ArgumentCaptor<SysOperationAuditLog> captor = ArgumentCaptor.forClass(SysOperationAuditLog.class);
        verify(operationAuditLogMapper).insert(captor.capture());
        SysOperationAuditLog saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals("default", saved.getTenantId());
        assertEquals("SUCCESS", saved.getResultStatus());
        assertNotNull(saved.getOperatedAt());
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
                1, 20, "admin", null, "/api/v1/users", "SUCCESS", null, null);

        assertEquals(1, result.getTotal());
        assertEquals("A001", result.getRecords().get(0).getId());
    }

    @Test
    void detail_shouldThrow_whenLogMissing() {
        when(operationAuditLogMapper.selectById("A404")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> operationAuditService.detail("A404"));

        assertEquals(40471, ex.getCode());
    }
}
