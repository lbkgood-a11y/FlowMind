package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionPolicyCheckerTest {

    private final ActionAuthorizationClient authorizationClient = mock(ActionAuthorizationClient.class);
    private final ActionPolicyChecker checker = new ActionPolicyChecker(authorizationClient);

    @Test
    void forwardsActionMetadataAsFirstClassAuthorizationFields() {
        AuthorizationDecisionResponse decision = new AuthorizationDecisionResponse();
        decision.setAllowed(true);
        when(authorizationClient.decide(org.mockito.Mockito.any())).thenReturn(decision);

        checker.check(definition(), request());

        ArgumentCaptor<AuthorizationDecisionRequest> captor =
                ArgumentCaptor.forClass(AuthorizationDecisionRequest.class);
        verify(authorizationClient).decide(captor.capture());
        AuthorizationDecisionRequest authz = captor.getValue();
        assertThat(authz.getResourceCode()).isEqualTo("PROCESS_TASK:TASK001");
        assertThat(authz.getActionId()).isEqualTo("act_001");
        assertThat(authz.getActionType()).isEqualTo("process.task.approve");
        assertThat(authz.getActionSource()).isEqualTo("GUI");
        assertThat(authz.getActionTargetType()).isEqualTo("PROCESS_TASK");
        assertThat(authz.getActionTargetId()).isEqualTo("TASK001");
        assertThat(authz.getActionCorrelationId()).isEqualTo("corr_001");
        assertThat(authz.getActionPayloadMetadata()).containsKey("payloadKeys");
        assertThat(authz.getActionPayloadMetadata().toString()).doesNotContain("plain-secret");
    }

    @Test
    void ignoresScopedAuthorizationResourceOutsideTargetNamespace() {
        AuthorizationDecisionResponse decision = new AuthorizationDecisionResponse();
        decision.setAllowed(true);
        when(authorizationClient.decide(org.mockito.Mockito.any())).thenReturn(decision);
        GlobalActionRequest request = request();
        request.getTarget().getAttributes().put(
                "authorizationResourceCode", "LOWCODE_FORM:LEAVE");

        checker.check(definition(), request);

        ArgumentCaptor<AuthorizationDecisionRequest> captor =
                ArgumentCaptor.forClass(AuthorizationDecisionRequest.class);
        verify(authorizationClient).decide(captor.capture());
        assertThat(captor.getValue().getResourceCode()).isEqualTo("PROCESS_TASK");
    }

    private ActionDefinition definition() {
        ActionDefinition definition = new ActionDefinition();
        definition.setActionType("process.task.approve");
        definition.setOwnerService("service-workflow-engine");
        definition.setTargetType("PROCESS_TASK");
        definition.setRequiredPermission("approve");
        return definition;
    }

    private GlobalActionRequest request() {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionId("act_001");
        request.setActionType("process.task.approve");
        request.setSource(ActionSource.GUI);
        request.setPayload(Map.of(
                "credential", Map.of("secret", "plain-secret"),
                "taskId", "TASK001"));
        ActionActor actor = new ActionActor();
        actor.setType(ActionActorType.USER);
        actor.setId("user-1");
        actor.setTenantId("tenant-a");
        request.setActor(actor);
        ActionTarget target = new ActionTarget();
        target.setType("PROCESS_TASK");
        target.setId("TASK001");
        target.setOwnerService("service-workflow-engine");
        target.setTenantId("tenant-a");
        target.getAttributes().put("authorizationResourceCode", "process_task:task001");
        request.setTarget(target);
        ActionContext context = new ActionContext();
        context.setTenantId("tenant-a");
        context.setTraceId("trace-001");
        context.setCorrelationId("corr_001");
        request.setContext(context);
        return request;
    }
}
