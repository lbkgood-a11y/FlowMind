package com.triobase.service.lowcode.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.ActionCandidateValidationResult;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.action.runtime.ActionExecutionContext;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.result.R;
import com.triobase.common.core.trace.TraceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LowcodeOwnerHostedActionControllerTest {

    private final LowcodeActionOwnerExecutionService executionService = mock(LowcodeActionOwnerExecutionService.class);
    private final LowcodeOwnerHostedActionController controller = new LowcodeOwnerHostedActionController(
            new ObjectMapper(),
            List.of(new LowcodeActionDefinitionProvider()),
            executionService,
            null);

    @AfterEach
    void tearDown() {
        ActionExecutionContext.clear();
        SecurityContextHolder.clear();
        TraceUtil.clear();
    }

    @Test
    void validateRejectsInvalidPayloadBeforeOwnerGuard() {
        ActionCandidate candidate = candidate();
        candidate.setPayload(Map.of("appKey", "leave"));

        R<ActionCandidateValidationResult> response = controller.validate(candidate);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().isDispatchable()).isFalse();
        assertThat(response.getData().getErrors()).extracting("code")
                .contains("ACTION_PAYLOAD_REQUIRED_MISSING");
        verify(executionService, never()).guard(any());
        verify(executionService, never()).execute(any());
    }

    @Test
    void dispatchCandidateRunsOwnerExecutorOnceForDuplicateIdempotencyKey() {
        AtomicInteger executeCount = new AtomicInteger();
        AtomicReference<ActionExecutionContext.Snapshot> snapshot = new AtomicReference<>();
        when(executionService.guard(any())).thenReturn(ActionOwnerGuardResponse.allowed("LOWCODE_FORM_ACTIONABLE"));
        when(executionService.execute(any())).thenAnswer(invocation -> {
            executeCount.incrementAndGet();
            snapshot.set(ActionExecutionContext.current());
            return success(invocation.getArgument(0), "leave-001");
        });
        ActionCandidate candidate = candidate();
        candidate.setIdempotencyKey("leave-submit-001");

        R<GlobalActionResult> first = controller.dispatchCandidate(candidate);
        R<GlobalActionResult> duplicate = controller.dispatchCandidate(candidate);

        assertThat(first.getCode()).isZero();
        assertThat(first.getData().getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(duplicate.getData().getActionId()).isEqualTo(first.getData().getActionId());
        assertThat(executeCount).hasValue(1);
        assertThat(snapshot.get().actionType()).isEqualTo("lowcode.form.submit");
        assertThat(snapshot.get().traceId()).isEqualTo("trace-leave-1");
        assertThat(SecurityContextHolder.getUserId()).isNull();
        assertThat(TraceUtil.getTraceId()).isNull();
    }

    @Test
    void dispatchReturnsRejectedResultWhenOwnerGuardDenies() {
        when(executionService.guard(any())).thenReturn(ActionOwnerGuardResponse.denied(
                "LOWCODE_FORM_LOCKED",
                "LOWCODE_FORM_LOCKED",
                List.of(ActionError.of("LOWCODE_FORM_LOCKED",
                        ActionErrorCategory.GUARD,
                        "LOWCODE_FORM_LOCKED"))));

        R<GlobalActionResult> response = controller.dispatch(request());

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getStatus()).isEqualTo(ActionStatus.REJECTED);
        assertThat(response.getData().getErrors()).extracting("code")
                .contains("LOWCODE_FORM_LOCKED");
        verify(executionService, never()).execute(any());
    }

    @Test
    void dispatchWrapsOwnerExceptionAsBoundedFailedResult() {
        when(executionService.guard(any())).thenReturn(ActionOwnerGuardResponse.allowed("LOWCODE_FORM_ACTIONABLE"));
        when(executionService.execute(any())).thenThrow(new IllegalStateException("database unavailable"));

        R<GlobalActionResult> response = controller.dispatch(request());

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(response.getData().getMessage()).isEqualTo("ACTION_DISPATCH_FAILED");
        assertThat(response.getData().getErrors()).extracting("category")
                .contains(ActionErrorCategory.SYSTEM);
    }

    private ActionCandidate candidate() {
        ActionCandidate candidate = new ActionCandidate();
        candidate.setActionType("lowcode.form.submit");
        candidate.setSource(ActionSource.LUI);
        candidate.setActor(actor());
        candidate.setTarget(target());
        candidate.setContext(context());
        candidate.setPayload(payload());
        return candidate;
    }

    private GlobalActionRequest request() {
        GlobalActionRequest request = candidate().toActionRequest();
        request.setIdempotencyKey("leave-submit-request-001");
        return request;
    }

    private GlobalActionResult success(GlobalActionRequest request, String instanceId) {
        GlobalActionResult response = new GlobalActionResult();
        response.setActionId(request.getActionId());
        response.setActionType(request.getActionType());
        response.setOwnerService("service-lowcode");
        response.setStatus(ActionStatus.SUCCEEDED);
        response.setOwnerExecutionRef(instanceId);
        response.setData(Map.of("instanceId", instanceId));
        response.setTargetStatus("SUBMITTED");
        response.setTargetStatusGroup("LOWCODE_FORM");
        response.getRefreshScopes().addAll(List.of("document", "actions", "timeline"));
        return response;
    }

    private Map<String, Object> payload() {
        return Map.of(
                "appKey", "leave",
                "version", 1,
                "actionCode", "submitAndLaunch",
                "data", Map.of("reason", "family"));
    }

    private ActionActor actor() {
        ActionActor actor = new ActionActor();
        actor.setType(ActionActorType.USER);
        actor.setId("U001");
        actor.setDisplayName("Alice");
        actor.setTenantId("tenant-a");
        return actor;
    }

    private ActionTarget target() {
        ActionTarget target = new ActionTarget();
        target.setType("LOWCODE_FORM");
        target.setId("leave");
        target.setOwnerService("service-lowcode");
        target.setTenantId("tenant-a");
        return target;
    }

    private ActionContext context() {
        ActionContext context = new ActionContext();
        context.setTenantId("tenant-a");
        context.setTraceId("trace-leave-1");
        context.setCorrelationId("corr-leave-1");
        context.setAuthVersion(1L);
        context.setRoleVersion(2L);
        context.setDataPolicyVersion(3L);
        return context;
    }
}
