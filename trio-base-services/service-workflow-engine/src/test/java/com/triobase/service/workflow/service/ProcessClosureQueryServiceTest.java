package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.workflow.entity.ClosureEffect;
import com.triobase.service.workflow.entity.ProcessClosure;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.ProcessOutcome;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.mapper.ClosureEffectMapper;
import com.triobase.service.workflow.mapper.ProcessClosureMapper;
import com.triobase.service.workflow.mapper.ProcessInstanceMapper;
import com.triobase.service.workflow.mapper.ProcessOutcomeMapper;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessClosureQueryServiceTest {

    private final ProcessOutcomeMapper processOutcomeMapper = mock(ProcessOutcomeMapper.class);
    private final ProcessClosureMapper processClosureMapper = mock(ProcessClosureMapper.class);
    private final ClosureEffectMapper closureEffectMapper = mock(ClosureEffectMapper.class);
    private final ProcessInstanceMapper processInstanceMapper = mock(ProcessInstanceMapper.class);
    private final ProcessPackageMapper processPackageMapper = mock(ProcessPackageMapper.class);
    private final ProcessBusinessAuthorizationService authorizationService =
            mock(ProcessBusinessAuthorizationService.class);
    private final ProcessClosureQueryService service = new ProcessClosureQueryService(
            processOutcomeMapper,
            processClosureMapper,
            closureEffectMapper,
            processInstanceMapper,
            processPackageMapper,
            authorizationService,
            new ObjectMapper());

    @Test
    void returnsOutcomeClosureEffectsAndRetryAvailability() {
        ProcessInstance instance = instance();
        when(processInstanceMapper.selectById("PI001")).thenReturn(instance);
        when(processOutcomeMapper.selectOne(any())).thenReturn(outcome());
        when(processClosureMapper.selectOne(any())).thenReturn(closure());
        when(closureEffectMapper.selectList(any())).thenReturn(List.of(effect()));
        when(processPackageMapper.selectById("PKG001")).thenReturn(pkg());

        var response = service.getByProcessInstanceId("PI001");

        verify(authorizationService).requireCanView(instance);
        assertEquals("APPROVED", response.getOutcome().getOutcomeStatus());
        assertEquals("act_task_001", response.getOutcome().getActionId());
        assertEquals("PARTIAL_FAILED", response.getClosure().getClosureStatus());
        assertEquals("act_task_001", response.getClosure().getActionId());
        assertEquals("notifyApplicant", response.getEffects().getFirst().getBusinessActionCode());
        assertEquals("act_retry_001", response.getEffects().getFirst().getActionId());
        assertEquals("通知申请人", response.getEffects().getFirst().getBusinessActionName());
        assertTrue(response.getEffects().getFirst().isRetryAvailable());
        assertTrue(response.getEffects().getFirst().isManualHandlingAvailable());
    }

    private ProcessInstance instance() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId("PI001");
        instance.setProcessPackageId("PKG001");
        instance.setProcessKey("expense_report");
        instance.setTenantId("TENANT_A");
        instance.setBusinessType("expense_report");
        instance.setBusinessId("ER100");
        return instance;
    }

    private ProcessOutcome outcome() {
        ProcessOutcome outcome = new ProcessOutcome();
        outcome.setId("OUT001");
        outcome.setProcessInstanceId("PI001");
        outcome.setProcessKey("expense_report");
        outcome.setProcessVersion(1);
        outcome.setBusinessType("expense_report");
        outcome.setBusinessId("ER100");
        outcome.setOutcomeStatus("APPROVED");
        outcome.setActionId("act_task_001");
        outcome.setActionType("process.task.approve");
        outcome.setActionSource("GUI");
        outcome.setActionActorType("USER");
        outcome.setActionActorId("approver-1");
        outcome.setActionActorName("Approver");
        outcome.setActionCorrelationId("corr-001");
        return outcome;
    }

    private ProcessClosure closure() {
        ProcessClosure closure = new ProcessClosure();
        closure.setId("CLO001");
        closure.setOutcomeId("OUT001");
        closure.setClosureStatus("PARTIAL_FAILED");
        closure.setBusinessType("expense_report");
        closure.setBusinessId("ER100");
        closure.setActionId("act_task_001");
        return closure;
    }

    private ClosureEffect effect() {
        ClosureEffect effect = new ClosureEffect();
        effect.setId("EFF001");
        effect.setClosureId("CLO001");
        effect.setEffectKey("approved.notify");
        effect.setEffectType("NOTIFICATION");
        effect.setBusinessActionCode("notifyApplicant");
        effect.setStatus("FAILED");
        effect.setRequestJson("{\"manualHandlingEnabled\":true}");
        effect.setAttemptCount(3);
        effect.setLastError("temporary failure");
        effect.setActionId("act_retry_001");
        effect.setActionType("process.closure.effect.retry");
        effect.setActionSource("GUI");
        effect.setActionActorType("USER");
        effect.setActionActorId("admin-1");
        effect.setActionActorName("Admin");
        effect.setActionCorrelationId("corr-retry-001");
        return effect;
    }

    private ProcessPackage pkg() {
        ProcessPackage pkg = new ProcessPackage();
        pkg.setId("PKG001");
        pkg.setClosurePlanJson("""
                {
                  "outcomes": {
                    "APPROVED": [
                      {
                        "effectKey": "approved.notify",
                        "selectorType": "ACTION",
                        "action": {
                          "actionCode": "notifyApplicant",
                          "displayName": "通知申请人"
                        }
                      }
                    ]
                  }
                }
                """);
        return pkg;
    }
}
