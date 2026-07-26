package com.triobase.service.openapi.controller;

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
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.result.R;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.action.OpenApiActionDefinitionProvider;
import com.triobase.common.action.runtime.ActionExecutionContext;
import com.triobase.service.openapi.action.OpenApiActionOwnerExecutionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenApiOwnerHostedActionControllerTest {

    private final OpenApiActionOwnerExecutionService executionService = mock(OpenApiActionOwnerExecutionService.class);
    private final OpenApiOwnerHostedActionController controller = new OpenApiOwnerHostedActionController(
            new ObjectMapper(),
            List.of(new OpenApiActionDefinitionProvider()),
            executionService,
            null);

    @AfterEach
    void tearDown() {
        ActionExecutionContext.clear();
        SecurityContextHolder.clear();
        TraceUtil.clear();
    }

    @Test
    void dispatchAppliesOpenApiActionContextAndRestoresThreadLocals() {
        AtomicReference<ActionExecutionContext.Snapshot> snapshot = new AtomicReference<>();
        AtomicReference<List<String>> roles = new AtomicReference<>();
        when(executionService.guard(any())).thenReturn(ActionOwnerGuardResponse.allowed("OPENAPI_ORCHESTRATION_STARTABLE"));
        when(executionService.execute(any())).thenAnswer(invocation -> {
            snapshot.set(ActionExecutionContext.current());
            roles.set(SecurityContextHolder.getRoles());
            return success(invocation.getArgument(0));
        });

        R<GlobalActionResult> response = controller.dispatch(request());

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(response.getData().getOwnerExecutionRef()).isEqualTo("exec-001");
        assertThat(snapshot.get().actionType()).isEqualTo("integration.orchestration.start");
        assertThat(snapshot.get().traceId()).isEqualTo("trace-openapi-owner-1");
        assertThat(roles.get()).containsExactly("OPENAPI_ACTION_OWNER");
        assertThat(ActionExecutionContext.current()).isNull();
        assertThat(SecurityContextHolder.getUserId()).isNull();
        assertThat(TraceUtil.getTraceId()).isNull();
    }

    private GlobalActionResult success(GlobalActionRequest request) {
        GlobalActionResult response = new GlobalActionResult();
        response.setActionId(request.getActionId());
        response.setActionType(request.getActionType());
        response.setOwnerService("service-openapi");
        response.setStatus(ActionStatus.SUCCEEDED);
        response.setOwnerExecutionRef("exec-001");
        response.setData(Map.of("runtimeStatus", "RUNNING"));
        return response;
    }

    private GlobalActionRequest request() {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionType("integration.orchestration.start");
        request.setSource(ActionSource.LUI);
        request.setIdempotencyKey("orch-start-001");
        request.setActor(actor());
        request.setTarget(target());
        request.setContext(context());
        request.setPayload(Map.of(
                "routeKey", "orders.submit",
                "environment", "PROD",
                "operation", "START",
                "idempotencyKey", "orch-start-001",
                "admission", Map.of("tenantId", "tenant-a", "environment", "PROD"),
                "payload", Map.of("orderId", "ORD001")));
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
        target.setType("INTEGRATION_ROUTE");
        target.setId("orders.submit");
        target.setOwnerService("service-openapi");
        target.setTenantId("tenant-a");
        return target;
    }

    private ActionContext context() {
        ActionContext context = new ActionContext();
        context.setTenantId("tenant-a");
        context.setTraceId("trace-openapi-owner-1");
        context.setCorrelationId("corr-openapi-owner-1");
        return context;
    }
}
