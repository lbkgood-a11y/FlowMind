package com.triobase.service.lowcode.action;

import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.action.owner.ActionOwnerDispatchResponse;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.trace.TraceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LowcodeActionOwnerControllerTest {

    private final LowcodeActionOwnerExecutionService executionService = mock(LowcodeActionOwnerExecutionService.class);
    private final LowcodeActionOwnerController controller = new LowcodeActionOwnerController(executionService);

    @AfterEach
    void tearDown() {
        LowcodeActionExecutionContext.clear();
        SecurityContextHolder.clear();
        TraceUtil.clear();
    }

    @Test
    void executeAppliesLowcodeActionContextThenClearsIt() {
        ActionOwnerDispatchRequest request = request();
        AtomicReference<LowcodeActionExecutionContext.Snapshot> snapshot = new AtomicReference<>();
        AtomicReference<String> trace = new AtomicReference<>();
        AtomicReference<String> user = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        ActionOwnerDispatchResponse ownerResponse = new ActionOwnerDispatchResponse();
        ownerResponse.setActionId("act-lowcode-1");
        ownerResponse.setOwnerService("service-lowcode");
        ownerResponse.setStatus(ActionStatus.SUCCEEDED);
        when(executionService.execute(request)).thenAnswer(invocation -> {
            snapshot.set(LowcodeActionExecutionContext.current());
            trace.set(TraceUtil.getTraceId());
            user.set(SecurityContextHolder.getUserId());
            tenant.set(SecurityContextHolder.getTenantId());
            return ownerResponse;
        });

        var response = controller.execute(request);

        assertThat(response.getData().getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(snapshot.get().actionId()).isEqualTo("act-lowcode-1");
        assertThat(snapshot.get().source()).isEqualTo("GUI");
        assertThat(snapshot.get().traceId()).isEqualTo("trace-lowcode-1");
        assertThat(snapshot.get().correlationId()).isEqualTo("corr-lowcode-1");
        assertThat(trace.get()).isEqualTo("trace-lowcode-1");
        assertThat(user.get()).isEqualTo("U001");
        assertThat(tenant.get()).isEqualTo("tenant-a");
        assertThat(LowcodeActionExecutionContext.current()).isNull();
        assertThat(TraceUtil.getTraceId()).isNull();
        assertThat(SecurityContextHolder.getUserId()).isNull();
    }

    private ActionOwnerDispatchRequest request() {
        ActionOwnerDispatchRequest request = new ActionOwnerDispatchRequest();
        request.setActionId("act-lowcode-1");
        request.setActionType("lowcode.form.submit");
        request.setOwnerService("service-lowcode");
        request.setSource(ActionSource.GUI);
        request.setPayload(Map.of("appKey", "expense_report"));
        ActionActor actor = new ActionActor();
        actor.setType(ActionActorType.USER);
        actor.setId("U001");
        actor.setDisplayName("Alice");
        request.setActor(actor);
        ActionContext context = new ActionContext();
        context.setTenantId("tenant-a");
        context.setTraceId("trace-lowcode-1");
        context.setCorrelationId("corr-lowcode-1");
        context.setAuthVersion(1L);
        context.setRoleVersion(2L);
        context.setDataPolicyVersion(3L);
        context.setAuthorizationVersion(4L);
        context.setFieldPolicyVersion(5L);
        context.setGuardTemplateVersion(6L);
        request.setContext(context);
        return request;
    }
}
