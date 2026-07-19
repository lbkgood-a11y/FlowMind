package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.service.workflow.action.WorkflowActionExecutionContext;
import com.triobase.service.workflow.entity.ClosureEffect;
import com.triobase.service.workflow.entity.ClosureOutbox;
import com.triobase.service.workflow.entity.ProcessClosure;
import com.triobase.service.workflow.entity.ProcessOutcome;
import com.triobase.service.workflow.executor.AgentFollowUpContext;
import com.triobase.service.workflow.executor.AgentFollowUpExecutor;
import com.triobase.service.workflow.executor.AgentFollowUpResult;
import com.triobase.service.workflow.executor.BusinessActionContext;
import com.triobase.service.workflow.executor.BusinessActionExecutor;
import com.triobase.service.workflow.executor.BusinessActionResult;
import com.triobase.service.workflow.executor.ProcessExecutorRegistry;
import com.triobase.service.workflow.mapper.ClosureEffectMapper;
import com.triobase.service.workflow.mapper.ClosureOutboxMapper;
import com.triobase.service.workflow.mapper.ProcessClosureMapper;
import com.triobase.service.workflow.mapper.ProcessOutcomeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClosureEffectExecutionServiceTest {

    private final ClosureEffectMapper closureEffectMapper = mock(ClosureEffectMapper.class);
    private final ProcessClosureMapper processClosureMapper = mock(ProcessClosureMapper.class);
    private final ProcessOutcomeMapper processOutcomeMapper = mock(ProcessOutcomeMapper.class);
    private final ClosureOutboxMapper closureOutboxMapper = mock(ClosureOutboxMapper.class);
    private final ProcessExecutorRegistry executorRegistry = mock(ProcessExecutorRegistry.class);
    private final ClosureEffectExecutionService service = new ClosureEffectExecutionService(
            closureEffectMapper,
            processClosureMapper,
            processOutcomeMapper,
            closureOutboxMapper,
            executorRegistry,
            new ObjectMapper());

    @AfterEach
    void tearDown() {
        WorkflowActionExecutionContext.clear();
    }

    @Test
    void executesBusinessActionEffectAndRecalculatesClosureStatus() {
        ClosureEffect effect = effect("HARD");
        ProcessClosure closure = closure();
        ProcessOutcome outcome = outcome();
        when(closureEffectMapper.selectById("EFF001")).thenReturn(effect);
        when(processClosureMapper.selectById("CLO001")).thenReturn(closure);
        when(processOutcomeMapper.selectById("OUT001")).thenReturn(outcome);
        when(closureEffectMapper.selectList(any())).thenReturn(List.of(effect));

        BusinessActionExecutor executor = mock(BusinessActionExecutor.class);
        when(executorRegistry.businessActionExecutor("expense_report.updateStatus"))
                .thenReturn(executor);
        when(executor.execute(any())).thenReturn(BusinessActionResult.succeeded(
                "EXPENSE_REPORT_STATUS_UPDATED",
                "ER100",
                Map.of("status", "APPROVED")));

        ClosureEffect result = service.executeEffect("EFF001");

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("SUCCEEDED", closure.getClosureStatus());
        ArgumentCaptor<BusinessActionContext> contextCaptor =
                ArgumentCaptor.forClass(BusinessActionContext.class);
        verify(executor).execute(contextCaptor.capture());
        assertEquals("idem-effect-1", contextCaptor.getValue().idempotencyKey());
        assertEquals("APPROVED", contextCaptor.getValue().parameters().get("status"));
        verify(processClosureMapper).updateById(closure);
    }

    @Test
    void missingSoftExecutorMarksRetryingWithDiagnosticDetails() {
        ClosureEffect effect = effect("ASYNC");
        ProcessClosure closure = closure();
        when(closureEffectMapper.selectById("EFF001")).thenReturn(effect);
        when(processClosureMapper.selectById("CLO001")).thenReturn(closure);
        when(processOutcomeMapper.selectById("OUT001")).thenReturn(outcome());
        when(closureEffectMapper.selectList(any())).thenReturn(List.of(effect));

        ClosureEffect result = service.executeEffect("EFF001");

        assertEquals("RETRYING", result.getStatus());
        assertEquals("CLOSURE_EFFECT_EXECUTOR_NOT_REGISTERED", result.getFailureCategory());
        assertNotNull(result.getNextRetryAt());
        assertEquals("RUNNING", closure.getClosureStatus());
    }

    @Test
    void missingHardExecutorMarksClosureFailed() {
        ClosureEffect effect = effect("HARD");
        ProcessClosure closure = closure();
        when(closureEffectMapper.selectById("EFF001")).thenReturn(effect);
        when(processClosureMapper.selectById("CLO001")).thenReturn(closure);
        when(processOutcomeMapper.selectById("OUT001")).thenReturn(outcome());
        when(closureEffectMapper.selectList(any())).thenReturn(List.of(effect));

        ClosureEffect result = service.executeEffect("EFF001");

        assertEquals("FAILED", result.getStatus());
        assertEquals("FAILED", closure.getClosureStatus());
        assertEquals("CLOSURE_EFFECT_EXECUTOR_NOT_REGISTERED", result.getFailureCategory());
    }

    @Test
    void duplicateSucceededDeliveryDoesNotInvokeExecutorAgain() {
        ClosureEffect effect = effect("ASYNC");
        effect.setStatus("SUCCEEDED");
        when(closureEffectMapper.selectById("EFF001")).thenReturn(effect);

        ClosureEffect result = service.executeEffect("EFF001");

        assertEquals("SUCCEEDED", result.getStatus());
        verifyNoInteractions(executorRegistry);
    }

    @Test
    void softRetryCanSucceedWithOriginalIdempotencyKey() {
        ClosureEffect effect = effect("ASYNC");
        effect.setStatus("RETRYING");
        effect.setAttemptCount(1);
        ProcessClosure closure = closure();
        ProcessOutcome outcome = outcome();
        when(closureEffectMapper.selectById("EFF001")).thenReturn(effect);
        when(processClosureMapper.selectById("CLO001")).thenReturn(closure);
        when(processOutcomeMapper.selectById("OUT001")).thenReturn(outcome);
        when(closureEffectMapper.selectList(any())).thenReturn(List.of(effect));

        BusinessActionExecutor executor = mock(BusinessActionExecutor.class);
        when(executorRegistry.businessActionExecutor("expense_report.updateStatus"))
                .thenReturn(executor);
        when(executor.execute(any())).thenReturn(BusinessActionResult.succeeded(
                "EXPENSE_REPORT_STATUS_UPDATED",
                "ER100",
                Map.of("status", "APPROVED")));

        ClosureEffect result = service.executeEffect("EFF001");

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(2, result.getAttemptCount());
        ArgumentCaptor<BusinessActionContext> contextCaptor =
                ArgumentCaptor.forClass(BusinessActionContext.class);
        verify(executor).execute(contextCaptor.capture());
        assertEquals("idem-effect-1", contextCaptor.getValue().idempotencyKey());
    }

    @Test
    void executesAgentFollowUpThroughRegisteredAgentExecutor() {
        ClosureEffect effect = effect("ASYNC");
        effect.setEffectType("AGENT_FOLLOW_UP");
        effect.setBusinessActionCode("paymentSummary");
        effect.setExecutorKey("expense_report.agent.paymentSummary");
        ProcessClosure closure = closure();
        ProcessOutcome outcome = outcome();
        when(closureEffectMapper.selectById("EFF001")).thenReturn(effect);
        when(processClosureMapper.selectById("CLO001")).thenReturn(closure);
        when(processOutcomeMapper.selectById("OUT001")).thenReturn(outcome);
        when(closureEffectMapper.selectList(any())).thenReturn(List.of(effect));

        AgentFollowUpExecutor executor = mock(AgentFollowUpExecutor.class);
        when(executorRegistry.agentFollowUpExecutor("expense_report.agent.paymentSummary"))
                .thenReturn(executor);
        when(executor.execute(any())).thenReturn(AgentFollowUpResult.succeeded(
                "AGENT_SUMMARY_CREATED",
                "ready to pay",
                Map.of("summary", "ready to pay")));

        ClosureEffect result = service.executeEffect("EFF001");

        assertEquals("SUCCEEDED", result.getStatus());
        assertTrue(result.getResultJson().contains("ready to pay"));
        ArgumentCaptor<AgentFollowUpContext> contextCaptor =
                ArgumentCaptor.forClass(AgentFollowUpContext.class);
        verify(executor).execute(contextCaptor.capture());
        assertEquals("paymentSummary", contextCaptor.getValue().agentActionCode());
        assertEquals("idem-effect-1", contextCaptor.getValue().idempotencyKey());
    }

    @Test
    void agentFollowUpRetryCanSucceedAfterSoftFailure() {
        ClosureEffect effect = effect("ASYNC");
        effect.setStatus("RETRYING");
        effect.setAttemptCount(1);
        effect.setEffectType("AGENT_FOLLOW_UP");
        effect.setBusinessActionCode("paymentSummary");
        effect.setExecutorKey("expense_report.agent.paymentSummary");
        ProcessClosure closure = closure();
        ProcessOutcome outcome = outcome();
        when(closureEffectMapper.selectById("EFF001")).thenReturn(effect);
        when(processClosureMapper.selectById("CLO001")).thenReturn(closure);
        when(processOutcomeMapper.selectById("OUT001")).thenReturn(outcome);
        when(closureEffectMapper.selectList(any())).thenReturn(List.of(effect));

        AgentFollowUpExecutor executor = mock(AgentFollowUpExecutor.class);
        when(executorRegistry.agentFollowUpExecutor("expense_report.agent.paymentSummary"))
                .thenReturn(executor);
        when(executor.execute(any())).thenReturn(AgentFollowUpResult.succeeded(
                "AGENT_SUMMARY_CREATED",
                "retry ready",
                Map.of("summary", "retry ready")));

        ClosureEffect result = service.executeEffect("EFF001");

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(2, result.getAttemptCount());
        assertTrue(result.getResultJson().contains("retry ready"));
    }

    @Test
    void manualHandlingRequiresPolicyFlagAndAuditsOriginalFailure() {
        ClosureEffect effect = effect("ASYNC");
        effect.setStatus("FAILED");
        effect.setFailureCategory("BROKER_TIMEOUT");
        effect.setLastError("temporary failure");
        effect.setRequestJson("{\"status\":\"APPROVED\",\"manualHandlingEnabled\":true}");
        ProcessClosure closure = closure();
        ClosureOutbox outbox = new ClosureOutbox();
        outbox.setId("OUTBOX001");
        outbox.setEffectId("EFF001");
        outbox.setStatus("FAILED");
        when(closureEffectMapper.selectById("EFF001")).thenReturn(effect);
        when(processClosureMapper.selectById("CLO001")).thenReturn(closure);
        when(closureOutboxMapper.selectList(any())).thenReturn(List.of(outbox));
        when(closureEffectMapper.selectList(any())).thenReturn(List.of(effect));
        WorkflowActionExecutionContext.set(ownerRequest());

        ClosureEffect result = service.markManuallyHandled(
                "EFF001",
                "线下通知已补发",
                "admin-1",
                "trace-manual");

        assertEquals("MANUALLY_HANDLED", result.getStatus());
        assertTrue(result.getResultJson().contains("BROKER_TIMEOUT"));
        assertTrue(result.getResultJson().contains("线下通知已补发"));
        assertEquals("SKIPPED", outbox.getStatus());
        assertEquals("SUCCEEDED", closure.getClosureStatus());
        assertEquals("act_closure_001", result.getActionId());
        assertEquals("process.closure.effect.markHandled", result.getActionType());
        assertEquals("act_closure_001", closure.getActionId());
        verify(closureEffectMapper).updateById(effect);
        verify(closureOutboxMapper).updateById(outbox);
        verify(processClosureMapper).updateById(closure);
    }

    private ClosureEffect effect(String mode) {
        ClosureEffect effect = new ClosureEffect();
        effect.setId("EFF001");
        effect.setClosureId("CLO001");
        effect.setEffectKey("approved.updateStatus");
        effect.setEffectType("BUSINESS_STATUS_UPDATE");
        effect.setTriggerOutcome("APPROVED");
        effect.setBusinessActionCode("updateStatus");
        effect.setExecutorKey("expense_report.updateStatus");
        effect.setMode(mode);
        effect.setStatus("PENDING");
        effect.setIdempotencyKey("idem-effect-1");
        effect.setRequestJson("{\"status\":\"APPROVED\"}");
        effect.setAttemptCount(0);
        effect.setTraceId("trace-1");
        return effect;
    }

    private ProcessClosure closure() {
        ProcessClosure closure = new ProcessClosure();
        closure.setId("CLO001");
        closure.setOutcomeId("OUT001");
        closure.setProcessInstanceId("PI001");
        closure.setBusinessType("expense_report");
        closure.setBusinessId("ER100");
        closure.setClosureStatus("PENDING");
        return closure;
    }

    private ProcessOutcome outcome() {
        ProcessOutcome outcome = new ProcessOutcome();
        outcome.setId("OUT001");
        outcome.setProcessInstanceId("PI001");
        outcome.setTenantId("TENANT_A");
        outcome.setBusinessType("expense_report");
        outcome.setBusinessId("ER100");
        outcome.setPayloadJson("{\"outcomeStatus\":\"APPROVED\"}");
        return outcome;
    }

    private ActionOwnerDispatchRequest ownerRequest() {
        ActionOwnerDispatchRequest request = new ActionOwnerDispatchRequest();
        request.setActionId("act_closure_001");
        request.setActionType("process.closure.effect.markHandled");
        request.setSource(ActionSource.GUI);
        ActionActor actor = new ActionActor();
        actor.setType(ActionActorType.USER);
        actor.setId("admin-1");
        actor.setDisplayName("Admin");
        request.setActor(actor);
        ActionContext context = new ActionContext();
        context.setTraceId("trace-action-1");
        context.setCorrelationId("corr-closure-001");
        request.setContext(context);
        return request;
    }
}
