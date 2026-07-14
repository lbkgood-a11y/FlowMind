package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.workflow.entity.ClosureEffect;
import com.triobase.service.workflow.entity.ClosureOutbox;
import com.triobase.service.workflow.entity.ProcessBusinessEvent;
import com.triobase.service.workflow.entity.ProcessClosure;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.ProcessOutcome;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.entity.TaskOperation;
import com.triobase.service.workflow.mapper.ClosureEffectMapper;
import com.triobase.service.workflow.mapper.ClosureOutboxMapper;
import com.triobase.service.workflow.mapper.ProcessBusinessEventMapper;
import com.triobase.service.workflow.mapper.ProcessClosureMapper;
import com.triobase.service.workflow.mapper.ProcessInstanceMapper;
import com.triobase.service.workflow.mapper.ProcessOutcomeMapper;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import com.triobase.service.workflow.mapper.TaskOperationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessOutcomeServiceTest {

    private final ProcessOutcomeMapper processOutcomeMapper = mock(ProcessOutcomeMapper.class);
    private final ProcessClosureMapper processClosureMapper = mock(ProcessClosureMapper.class);
    private final ClosureEffectMapper closureEffectMapper = mock(ClosureEffectMapper.class);
    private final ClosureOutboxMapper closureOutboxMapper = mock(ClosureOutboxMapper.class);
    private final ProcessBusinessEventMapper processBusinessEventMapper =
            mock(ProcessBusinessEventMapper.class);
    private final ClosureEffectExecutionService closureEffectExecutionService =
            mock(ClosureEffectExecutionService.class);
    private final ProcessInstanceMapper processInstanceMapper = mock(ProcessInstanceMapper.class);
    private final ProcessPackageMapper processPackageMapper = mock(ProcessPackageMapper.class);
    private final TaskOperationMapper taskOperationMapper = mock(TaskOperationMapper.class);
    private final ProcessOutcomeService service = new ProcessOutcomeService(
            processOutcomeMapper,
            processClosureMapper,
            closureEffectMapper,
            closureOutboxMapper,
            processBusinessEventMapper,
            closureEffectExecutionService,
            processInstanceMapper,
            processPackageMapper,
            taskOperationMapper,
            new ObjectMapper());

    @AfterEach
    void tearDown() {
        TraceUtil.clear();
    }

    @Test
    void createsOutcomeClosureAndEffectsFromPublishedClosurePlan() {
        TraceUtil.setTraceId("trace-closure-1");
        when(processOutcomeMapper.selectOne(any())).thenReturn(null);
        when(processInstanceMapper.selectById("PI001")).thenReturn(instance());
        when(processPackageMapper.selectById("PKG001")).thenReturn(packageWithClosurePlan());
        TaskOperation operation = new TaskOperation();
        operation.setOperatorId("approver-1");
        when(taskOperationMapper.selectOne(any())).thenReturn(operation);

        ProcessOutcome outcome = service.createOutcome("PI001", "APPROVED", null);

        assertEquals("APPROVED", outcome.getOutcomeStatus());
        assertEquals("expense_report", outcome.getBusinessType());
        assertEquals("ER100", outcome.getBusinessId());
        assertEquals("TENANT_A", outcome.getTenantId());
        assertEquals("approver-1", outcome.getLastOperatorId());
        assertEquals("trace-closure-1", outcome.getTraceId());

        ArgumentCaptor<ProcessClosure> closureCaptor =
                ArgumentCaptor.forClass(ProcessClosure.class);
        verify(processClosureMapper).insert(closureCaptor.capture());
        assertEquals("PENDING", closureCaptor.getValue().getClosureStatus());
        assertEquals("ER100", closureCaptor.getValue().getBusinessId());

        ArgumentCaptor<ClosureEffect> effectCaptor =
                ArgumentCaptor.forClass(ClosureEffect.class);
        verify(closureEffectMapper).insert(effectCaptor.capture());
        ClosureEffect effect = effectCaptor.getValue();
        assertEquals("BUSINESS_STATUS_UPDATE", effect.getEffectType());
        assertEquals("updateStatus", effect.getBusinessActionCode());
        assertEquals("expense_report.updateStatus", effect.getExecutorKey());
        assertEquals("HARD", effect.getMode());
        assertEquals("PI001:APPROVED:approved.updateStatus", effect.getIdempotencyKey());
        verify(closureEffectExecutionService).executeEffect(effect.getId());
        verify(closureOutboxMapper, never()).insert(any(ClosureOutbox.class));

        ArgumentCaptor<ProcessBusinessEvent> eventCaptor =
                ArgumentCaptor.forClass(ProcessBusinessEvent.class);
        verify(processBusinessEventMapper, org.mockito.Mockito.times(2)).insert(eventCaptor.capture());
        assertEquals("ProcessOutcomeCreated", eventCaptor.getAllValues().get(0).getEventType());
        assertEquals("ProcessClosureCreated", eventCaptor.getAllValues().get(1).getEventType());
        assertEquals("AVAILABLE", eventCaptor.getAllValues().get(0).getStatus());
        assertEquals("APPROVED", eventCaptor.getAllValues().get(0).getOutcomeStatus());
    }

    @Test
    void duplicateTerminalEventReusesExistingOutcome() {
        ProcessOutcome existing = new ProcessOutcome();
        existing.setId("OUT001");
        existing.setProcessInstanceId("PI001");
        existing.setOutcomeStatus("APPROVED");
        when(processOutcomeMapper.selectOne(any())).thenReturn(existing);

        ProcessOutcome result = service.createOutcome("PI001", "APPROVED", null);

        assertEquals("OUT001", result.getId());
        verify(processOutcomeMapper, never()).insert(any(ProcessOutcome.class));
        verify(processClosureMapper, never()).insert(any(ProcessClosure.class));
        verify(closureEffectMapper, never()).insert(any(ClosureEffect.class));
        verify(processBusinessEventMapper, never()).insert(any(ProcessBusinessEvent.class));
        verify(closureEffectExecutionService, never()).executeEffect(any());
    }

    @Test
    void createsRejectedAndSuspendedOutcomesWithoutClosurePlan() {
        when(processOutcomeMapper.selectOne(any())).thenReturn(null);
        when(processInstanceMapper.selectById("PI_REJECTED")).thenReturn(instance("PI_REJECTED"));
        when(processInstanceMapper.selectById("PI_SUSPENDED")).thenReturn(instance("PI_SUSPENDED"));
        ProcessPackage pkg = new ProcessPackage();
        pkg.setId("PKG001");
        when(processPackageMapper.selectById("PKG001")).thenReturn(pkg);

        ProcessOutcome rejected = service.createOutcome("PI_REJECTED", "REJECTED", "not approved");
        ProcessOutcome suspended = service.createOutcome("PI_SUSPENDED", "SUSPENDED", "condition failed");

        assertEquals("REJECTED", rejected.getOutcomeStatus());
        assertEquals("SUSPENDED", suspended.getOutcomeStatus());
        verify(processClosureMapper, never()).insert(any(ProcessClosure.class));
        verify(closureEffectMapper, never()).insert(any(ClosureEffect.class));
    }

    private ProcessInstance instance() {
        return instance("PI001");
    }

    private ProcessInstance instance(String id) {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(id);
        instance.setProcessPackageId("PKG001");
        instance.setProcessKey("expense_report");
        instance.setVersion(1);
        instance.setTenantId("TENANT_A");
        instance.setBusinessType("expense_report");
        instance.setBusinessId("ER100");
        instance.setInitiatorId("user-1");
        return instance;
    }

    private ProcessPackage packageWithClosurePlan() {
        ProcessPackage pkg = new ProcessPackage();
        pkg.setId("PKG001");
        pkg.setClosurePlanJson("""
                {
                  "outcomes": {
                    "APPROVED": [{
                      "effectKey": "approved.updateStatus",
                      "mode": "HARD",
                      "params": {"status": "APPROVED"},
                      "selectorType": "ACTION",
                      "action": {
                        "actionCode": "updateStatus",
                        "displayName": "更新报销单状态",
                        "actionType": "UPDATE_STATUS",
                        "executorKey": "expense_report.updateStatus"
                      }
                    }]
                  }
                }
                """);
        return pkg;
    }
}
