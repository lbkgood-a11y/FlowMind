package com.triobase.service.openapi.controller;

import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.action.owner.ActionOwnerDispatchResponse;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.action.OpenApiActionExecutionContext;
import com.triobase.service.openapi.action.OpenApiActionOwnerExecutionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenApiActionOwnerControllerTest {

    private final OpenApiActionOwnerExecutionService executionService = mock(OpenApiActionOwnerExecutionService.class);
    private final OpenApiActionOwnerController controller = new OpenApiActionOwnerController(executionService);

    @AfterEach
    void tearDown() {
        OpenApiActionExecutionContext.clear();
        SecurityContextHolder.clear();
        TraceUtil.clear();
    }

    @Test
    void executeAppliesOpenApiActionContextThenClearsIt() {
        ActionOwnerDispatchRequest request = request();
        AtomicReference<OpenApiActionExecutionContext.Snapshot> snapshot = new AtomicReference<>();
        AtomicReference<String> trace = new AtomicReference<>();
        AtomicReference<String> user = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        AtomicReference<java.util.List<String>> roles = new AtomicReference<>();
        ActionOwnerDispatchResponse ownerResponse = new ActionOwnerDispatchResponse();
        ownerResponse.setActionId("act-openapi-1");
        ownerResponse.setOwnerService("service-openapi");
        ownerResponse.setStatus(ActionStatus.SUCCEEDED);
        when(executionService.execute(request)).thenAnswer(invocation -> {
            snapshot.set(OpenApiActionExecutionContext.current());
            trace.set(TraceUtil.getTraceId());
            user.set(SecurityContextHolder.getUserId());
            tenant.set(SecurityContextHolder.getTenantId());
            roles.set(SecurityContextHolder.getRoles());
            return ownerResponse;
        });

        var response = controller.execute(request);

        assertThat(response.getData().getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(snapshot.get().actionId()).isEqualTo("act-openapi-1");
        assertThat(snapshot.get().source()).isEqualTo("LUI");
        assertThat(snapshot.get().traceId()).isEqualTo("trace-openapi-1");
        assertThat(snapshot.get().correlationId()).isEqualTo("corr-openapi-1");
        assertThat(trace.get()).isEqualTo("trace-openapi-1");
        assertThat(user.get()).isEqualTo("U001");
        assertThat(tenant.get()).isEqualTo("tenant-a");
        assertThat(roles.get()).containsExactly("OPENAPI_ACTION_OWNER");
        assertThat(OpenApiActionExecutionContext.current()).isNull();
        assertThat(TraceUtil.getTraceId()).isNull();
        assertThat(SecurityContextHolder.getUserId()).isNull();
    }

    private ActionOwnerDispatchRequest request() {
        ActionOwnerDispatchRequest request = new ActionOwnerDispatchRequest();
        request.setActionId("act-openapi-1");
        request.setActionType("integration.orchestration.start");
        request.setOwnerService("service-openapi");
        request.setSource(ActionSource.LUI);
        request.setPayload(Map.of("routeKey", "orders.submit"));
        ActionActor actor = new ActionActor();
        actor.setType(ActionActorType.USER);
        actor.setId("U001");
        actor.setDisplayName("Alice");
        actor.setTenantId("tenant-a");
        request.setActor(actor);
        ActionContext context = new ActionContext();
        context.setTenantId("tenant-a");
        context.setTraceId("trace-openapi-1");
        context.setCorrelationId("corr-openapi-1");
        context.setAuthVersion(1L);
        context.setRoleVersion(2L);
        context.setDataPolicyVersion(3L);
        request.setContext(context);
        return request;
    }
}
