package com.triobase.service.openapi.action;

import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.service.openapi.domain.entity.AuditEvent;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.entity.ExecutionStepAttempt;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiActionMetadataTest {

    @AfterEach
    void tearDown() {
        OpenApiActionExecutionContext.clear();
    }

    @Test
    void appliesCurrentActionMetadataToRuntimeRecords() {
        OpenApiActionExecutionContext.set(request());

        IntegrationExecution execution = new IntegrationExecution();
        ExecutionStepAttempt attempt = new ExecutionStepAttempt();
        CallbackInbox inbox = new CallbackInbox();
        AuditEvent event = new AuditEvent();
        OpenApiActionMetadata.apply(execution);
        OpenApiActionMetadata.apply(attempt);
        OpenApiActionMetadata.apply(inbox);
        OpenApiActionMetadata.apply(event);

        assertThat(execution.getActionId()).isEqualTo("act_001");
        assertThat(attempt.getActionTraceId()).isEqualTo("trace-001");
        assertThat(inbox.getActionActorId()).isEqualTo("client-a");
        assertThat(event.getActionCorrelationId()).isEqualTo("corr-001");
    }

    private ActionOwnerDispatchRequest request() {
        ActionOwnerDispatchRequest request = new ActionOwnerDispatchRequest();
        request.setActionId("act_001");
        request.setActionType("integration.orchestration.start");
        request.setSource(ActionSource.API);
        ActionActor actor = new ActionActor();
        actor.setType(ActionActorType.SERVICE);
        actor.setId("client-a");
        actor.setDisplayName("client-a");
        request.setActor(actor);
        ActionContext context = new ActionContext();
        context.setTraceId("trace-001");
        context.setCorrelationId("corr-001");
        request.setContext(context);
        return request;
    }
}
