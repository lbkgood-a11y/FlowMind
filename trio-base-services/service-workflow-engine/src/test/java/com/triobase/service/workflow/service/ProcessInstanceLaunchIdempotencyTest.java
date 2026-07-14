package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.workflow.dto.StartProcessRequest;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.mapper.NodeRecordMapper;
import com.triobase.service.workflow.mapper.ProcessInstanceMapper;
import com.triobase.service.workflow.mapper.TaskOperationMapper;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessInstanceLaunchIdempotencyTest {

    private final ProcessInstanceMapper processInstanceMapper = mock(ProcessInstanceMapper.class);
    private final ProcessPackageService processPackageService = mock(ProcessPackageService.class);
    private final ProcessFormDataValidator processFormDataValidator = mock(ProcessFormDataValidator.class);
    private final BusinessLaunchRuntimeService businessLaunchRuntimeService =
            mock(BusinessLaunchRuntimeService.class);
    private final ProcessBusinessAuthorizationService processBusinessAuthorizationService =
            mock(ProcessBusinessAuthorizationService.class);
    private final ProcessInstanceService service = new ProcessInstanceService(
            processInstanceMapper,
            mock(NodeRecordMapper.class),
            mock(TaskOperationMapper.class),
            processPackageService,
            mock(WorkflowClient.class),
            new ObjectMapper(),
            processFormDataValidator,
            businessLaunchRuntimeService,
            processBusinessAuthorizationService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void duplicateLaunchIdempotencyKeyReturnsExistingInstanceBeforeSideEffects() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of("/api/v1/process-instances/start:POST"),
                null,
                null,
                null));

        ProcessPackage pkg = new ProcessPackage();
        pkg.setId("PKG001");
        pkg.setProcessKey("expense_report");
        pkg.setVersion(1);
        when(processPackageService.findPublishedByKey("expense_report")).thenReturn(pkg);

        ProcessInstance existing = new ProcessInstance();
        existing.setId("PI001");
        existing.setProcessPackageId("PKG001");
        existing.setProcessKey("expense_report");
        existing.setProcessName("Expense Report");
        existing.setVersion(1);
        existing.setStatus("RUNNING");
        existing.setBusinessType("expense_report");
        existing.setBusinessId("ER100");
        existing.setLaunchIdempotencyKey("idem-1");
        when(processInstanceMapper.selectOne(any())).thenReturn(existing);

        StartProcessRequest request = new StartProcessRequest();
        request.setProcessKey("expense_report");
        request.setIdempotencyKey("idem-1");

        var response = service.startProcess(request);

        assertEquals("PI001", response.getId());
        assertEquals("ER100", response.getBusinessId());
        verify(processFormDataValidator, never()).validate(any(), any());
        verify(businessLaunchRuntimeService, never()).prepareLaunch(any(), any(), any());
        verify(processInstanceMapper, never()).insert(any(ProcessInstance.class));
    }
}
