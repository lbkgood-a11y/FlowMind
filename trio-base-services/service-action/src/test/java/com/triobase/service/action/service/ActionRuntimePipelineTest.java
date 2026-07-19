package com.triobase.service.action.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.definition.ActionSensitivePath;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.service.action.entity.ActionDispatch;
import com.triobase.service.action.entity.ActionExecution;
import com.triobase.service.action.exception.ActionRuntimeException;
import com.triobase.service.action.mapper.ActionExecutionMapper;
import com.triobase.service.action.repository.ActionEventRepository;
import com.triobase.service.action.repository.ActionExecutionRepository;
import com.triobase.service.action.support.ActionSecurityContextPropagator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionRuntimePipelineTest {

    @Mock
    private ActionExecutionRepository executionRepository;
    @Mock
    private ActionEventRepository eventRepository;
    @Mock
    private ActionPolicyChecker policyChecker;
    @Mock
    private ActionIdempotencyGuard idempotencyGuard;
    @Mock
    private ActionDispatchService dispatchService;
    @Mock
    private ActionOwnerDispatcher ownerDispatcher;
    @Mock
    private ActionExecutionMapper executionMapper;

    private ActionDefinition definition;
    private ActionRuntimePipeline pipeline;
    private ActionAuditRecorder auditRecorder;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        definition = definition();
        ActionDefinitionRegistry registry = new ActionDefinitionRegistry(List.of());
        registry.register(definition);
        auditRecorder = new ActionAuditRecorder(executionMapper, objectMapper);
        pipeline = new ActionRuntimePipeline(
                new ActionSecurityContextPropagator(),
                registry,
                new ActionPayloadValidator(objectMapper),
                policyChecker,
                idempotencyGuard,
                executionRepository,
                eventRepository,
                new ActionStatusService(executionMapper),
                auditRecorder,
                dispatchService,
                ownerDispatcher,
                new ActionResultFactory(objectMapper));
    }

    @Test
    void rejectsInvalidPayloadBeforeAuthorizationAndDispatch() {
        ActionExecution execution = execution();
        when(idempotencyGuard.duplicateResult(any(GlobalActionRequest.class))).thenReturn(Optional.empty());
        when(executionRepository.createIfAbsent(any(GlobalActionRequest.class), anyString()))
                .thenReturn(new ActionExecutionRepository.CreateResult(execution, true));

        GlobalActionRequest request = request(Map.of("note", "missing amount"));

        GlobalActionResult result = pipeline.submit(request);

        assertThat(result.getStatus()).isEqualTo(ActionStatus.REJECTED);
        assertThat(result.getErrors()).extracting(ActionError::getCode)
                .contains("ACTION_PAYLOAD_REQUIRED_MISSING");
        verify(policyChecker, never()).check(any(), any());
        verify(ownerDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    void rejectsDeniedAuthorizationBeforeOwnerDispatch() {
        ActionExecution execution = execution();
        when(idempotencyGuard.duplicateResult(any(GlobalActionRequest.class))).thenReturn(Optional.empty());
        when(executionRepository.createIfAbsent(any(GlobalActionRequest.class), anyString()))
                .thenReturn(new ActionExecutionRepository.CreateResult(execution, true));
        ActionError denied = ActionError.of("AUTHZ_DENIED",
                ActionErrorCategory.AUTHORIZATION,
                "denied");
        when(policyChecker.check(eq(definition), any(GlobalActionRequest.class)))
                .thenReturn(ActionPolicyDecision.denied(null, List.of(denied)));

        GlobalActionResult result = pipeline.submit(request(Map.of("amount", 100)));

        assertThat(result.getStatus()).isEqualTo(ActionStatus.REJECTED);
        assertThat(result.getErrors()).extracting(ActionError::getCode).contains("AUTHZ_DENIED");
        verify(ownerDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    void reusesDuplicateIdempotentResultBeforeCreatingExecution() {
        GlobalActionResult duplicate = new GlobalActionResult();
        duplicate.setActionId("act_existing");
        duplicate.setActionType(definition.getActionType());
        duplicate.setStatus(ActionStatus.SUCCEEDED);
        when(idempotencyGuard.duplicateResult(any(GlobalActionRequest.class))).thenReturn(Optional.of(duplicate));

        GlobalActionResult result = pipeline.submit(request(Map.of("amount", 100)));

        assertThat(result.getActionId()).isEqualTo("act_existing");
        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        verify(executionRepository, never()).createIfAbsent(any(), anyString());
        verify(ownerDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    void recordsSuccessfulOwnerDispatch() {
        ActionExecution execution = execution();
        ActionDispatch dispatch = dispatch();
        when(idempotencyGuard.duplicateResult(any(GlobalActionRequest.class))).thenReturn(Optional.empty());
        when(executionRepository.createIfAbsent(any(GlobalActionRequest.class), anyString()))
                .thenReturn(new ActionExecutionRepository.CreateResult(execution, true));
        when(policyChecker.check(eq(definition), any(GlobalActionRequest.class)))
                .thenReturn(ActionPolicyDecision.allowed(null));
        when(dispatchService.createDispatch(eq(execution), eq(definition))).thenReturn(dispatch);
        when(dispatchService.markDispatched(dispatch)).thenReturn(dispatch);
        when(dispatchService.markCompleted(dispatch)).thenReturn(dispatch);
        GlobalActionResult ownerResult = new GlobalActionResult();
        ownerResult.setStatus(ActionStatus.SUCCEEDED);
        ownerResult.setOwnerExecutionRef("owner-1");
        ownerResult.setTargetStatus("SUBMITTED");
        ownerResult.setTargetStatusGroup("PENDING");
        ownerResult.getRefreshScopes().addAll(List.of("document", "actions"));
        ownerResult.getOwnerExecutionMetadata().put("workflowId", "wf-1");
        ownerResult.getData().put("formInstanceId", "F1");
        when(ownerDispatcher.dispatch(eq(definition), any(GlobalActionRequest.class), eq(execution)))
                .thenReturn(ownerResult);

        GlobalActionResult result = pipeline.submit(request(Map.of("amount", 100)));

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(result.getActionId()).isEqualTo("act_1");
        assertThat(result.getOwnerService()).isEqualTo("service-lowcode");
        assertThat(result.getData()).containsEntry("formInstanceId", "F1");
        assertThat(result.getTargetStatus()).isEqualTo("SUBMITTED");
        assertThat(result.getRefreshScopes()).containsExactly("document", "actions");
        assertThat(result.getOwnerExecutionMetadata()).containsEntry("workflowId", "wf-1");
        verify(dispatchService).markCompleted(dispatch);
    }

    @Test
    void recordsFailedOwnerDispatchAsRetryableDispatchFailure() {
        ActionExecution execution = execution();
        ActionDispatch dispatch = dispatch();
        when(idempotencyGuard.duplicateResult(any(GlobalActionRequest.class))).thenReturn(Optional.empty());
        when(executionRepository.createIfAbsent(any(GlobalActionRequest.class), anyString()))
                .thenReturn(new ActionExecutionRepository.CreateResult(execution, true));
        when(policyChecker.check(eq(definition), any(GlobalActionRequest.class)))
                .thenReturn(ActionPolicyDecision.allowed(null));
        when(dispatchService.createDispatch(eq(execution), eq(definition))).thenReturn(dispatch);
        when(dispatchService.markDispatched(dispatch)).thenReturn(dispatch);
        when(dispatchService.markFailed(eq(dispatch), anyString(), eq(true))).thenReturn(dispatch);
        when(ownerDispatcher.dispatch(eq(definition), any(GlobalActionRequest.class), eq(execution)))
                .thenThrow(new ActionRuntimeException(50242,
                        ActionErrorCategory.DISPATCH,
                        "OWNER_DOWN"));

        GlobalActionResult result = pipeline.submit(request(Map.of("amount", 100)));

        assertThat(result.getStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getErrors()).extracting(ActionError::getCode)
                .contains("ACTION_DISPATCH_FAILED");
        verify(dispatchService).markFailed(dispatch, "OWNER_DOWN", true);
    }

    @Test
    void redactsSensitivePayloadPathsInAuditSummary() {
        GlobalActionRequest request = request(Map.of(
                "amount", 100,
                "credential", Map.of("secret", "plain-secret")));

        String summary = auditRecorder.redactedPayloadSummary(definition, request);

        assertThat(summary).contains("***REDACTED***");
        assertThat(summary).doesNotContain("plain-secret");
    }

    private ActionDefinition definition() {
        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setActionType("lowcode.form.submit");
        actionDefinition.setOwnerService("service-lowcode");
        actionDefinition.setTargetType("LOWCODE_FORM");
        actionDefinition.setRequiredPermission("SUBMIT");
        actionDefinition.setPayloadSchemaJson("""
                {
                  "type": "object",
                  "required": ["amount"],
                  "additionalProperties": false,
                  "properties": {
                    "amount": {"type": "number"},
                    "credential": {
                      "type": "object",
                      "properties": {
                        "secret": {"type": "string"}
                      }
                    }
                  }
                }
                """);
        actionDefinition.setSensitivePayloadPaths(List.of(ActionSensitivePath.of("credential.secret")));
        return actionDefinition;
    }

    private GlobalActionRequest request(Map<String, Object> payload) {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionType("lowcode.form.submit");
        request.setSource(ActionSource.GUI);
        request.setIdempotencyKey("idem-1");
        request.setPayload(payload);

        ActionActor actor = new ActionActor();
        actor.setType(ActionActorType.USER);
        actor.setId("U1");
        actor.setTenantId("T1");
        request.setActor(actor);

        ActionTarget target = new ActionTarget();
        target.setType("LOWCODE_FORM");
        target.setId("expense");
        target.setOwnerService("service-lowcode");
        target.setTenantId("T1");
        request.setTarget(target);

        ActionContext context = new ActionContext();
        context.setTenantId("T1");
        context.setTraceId("trace-1");
        context.setCorrelationId("corr-1");
        request.setContext(context);
        return request;
    }

    private ActionExecution execution() {
        ActionExecution execution = new ActionExecution();
        execution.setId("act_1");
        execution.setTenantId("T1");
        execution.setActionType("lowcode.form.submit");
        execution.setSource("GUI");
        execution.setStatus(ActionStatus.CREATED.name());
        execution.setTargetType("LOWCODE_FORM");
        execution.setTargetId("expense");
        execution.setTargetOwnerService("service-lowcode");
        execution.setOwnerService("service-lowcode");
        execution.setTraceId("trace-1");
        execution.setCorrelationId("corr-1");
        execution.setRetryable(false);
        return execution;
    }

    private ActionDispatch dispatch() {
        ActionDispatch dispatch = new ActionDispatch();
        dispatch.setId("dsp_1");
        dispatch.setActionId("act_1");
        dispatch.setTenantId("T1");
        dispatch.setOwnerService("service-lowcode");
        dispatch.setDispatchStatus(ActionDispatchService.STATUS_PENDING);
        dispatch.setAttemptCount(0);
        dispatch.setMaxAttempts(2);
        return dispatch;
    }
}
