package com.triobase.service.workflow.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowOwnerHostedActionControllerTest {

    private final WorkflowActionOwnerExecutionService executionService = mock(WorkflowActionOwnerExecutionService.class);
    private final WorkflowOwnerHostedActionController controller = new WorkflowOwnerHostedActionController(
            new ObjectMapper(),
            List.of(new WorkflowActionDefinitionProvider()),
            executionService,
            null);

    @AfterEach
    void tearDown() {
        ActionExecutionContext.clear();
        SecurityContextHolder.clear();
        TraceUtil.clear();
    }

    @Test
    void dispatchAppliesWorkflowActionContextAndRestoresThreadLocals() {
        AtomicReference<ActionExecutionContext.Snapshot> snapshot = new AtomicReference<>();
        AtomicReference<List<String>> permissions = new AtomicReference<>();
        when(executionService.guard(any())).thenReturn(ActionOwnerGuardResponse.allowed("PROCESS_TASK_ACTIONABLE"));
        when(executionService.execute(any())).thenAnswer(invocation -> {
            snapshot.set(ActionExecutionContext.current());
            permissions.set(SecurityContextHolder.getPermissions());
            return success(invocation.getArgument(0));
        });

        R<GlobalActionResult> response = controller.dispatch(request());

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(response.getData().getOwnerExecutionRef()).isEqualTo("TASK001");
        assertThat(snapshot.get().actionType()).isEqualTo("process.task.approve");
        assertThat(snapshot.get().traceId()).isEqualTo("trace-workflow-owner-1");
        assertThat(permissions.get()).containsExactly("/api/v1/tasks/*/approve:POST");
        assertThat(ActionExecutionContext.current()).isNull();
        assertThat(SecurityContextHolder.getUserId()).isNull();
        assertThat(TraceUtil.getTraceId()).isNull();
    }

    private GlobalActionResult success(GlobalActionRequest request) {
        GlobalActionResult response = new GlobalActionResult();
        response.setActionId(request.getActionId());
        response.setActionType(request.getActionType());
        response.setOwnerService("service-workflow-engine");
        response.setStatus(ActionStatus.SUCCEEDED);
        response.setOwnerExecutionRef("TASK001");
        response.setData(Map.of("runtimeStatus", "APPROVED"));
        return response;
    }

    private GlobalActionRequest request() {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionType("process.task.approve");
        request.setSource(ActionSource.GUI);
        request.setIdempotencyKey("approve-task-001");
        request.setActor(actor());
        request.setTarget(target());
        request.setContext(context());
        request.setPayload(Map.of("taskId", "TASK001", "comment", "ok"));
        return request;
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
        target.setType("PROCESS_TASK");
        target.setId("TASK001");
        target.setOwnerService("service-workflow-engine");
        target.setTenantId("tenant-a");
        return target;
    }

    private ActionContext context() {
        ActionContext context = new ActionContext();
        context.setTenantId("tenant-a");
        context.setTraceId("trace-workflow-owner-1");
        context.setCorrelationId("corr-workflow-owner-1");
        return context;
    }
}
