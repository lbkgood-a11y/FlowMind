package com.triobase.service.openapi.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionEventType;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionEventPayload;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.service.openapi.domain.entity.AuditEvent;
import com.triobase.service.openapi.infrastructure.mapper.AuditEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpenApiOwnerActionAuditSinkTest {

    private final AuditEventMapper mapper = mock(AuditEventMapper.class);
    private final OpenApiOwnerActionAuditSink sink = new OpenApiOwnerActionAuditSink(
            mapper,
            new ObjectMapper().findAndRegisterModules(),
            mock(RestTemplate.class),
            new InternalServiceSecurityProperties());

    @Test
    void persistsOwnerActionEventIntoIntegrationAuditTable() {
        sink.emit(event());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mapper).insert(captor.capture());
        AuditEvent audit = captor.getValue();
        assertThat(audit.getActionId()).isEqualTo("act_3");
        assertThat(audit.getActionType()).isEqualTo("integration.orchestration.start");
        assertThat(audit.getResourceType()).isEqualTo("OPENAPI_ORCHESTRATION");
        assertThat(audit.getResourceId()).isEqualTo("orch-1");
        assertThat(audit.getTraceId()).isEqualTo("trace-3");
        assertThat(audit.getActionCorrelationId()).isEqualTo("corr-3");
        assertThat(audit.getActorType()).isEqualTo("APPLICATION");
        assertThat(audit.getOutcome()).isEqualTo("SUCCESS");
    }

    private ActionEventPayload event() {
        ActionEventPayload event = new ActionEventPayload();
        event.setEventId("evt_3");
        event.setActionId("act_3");
        event.setEventType(ActionEventType.ACCEPTED);
        event.setStatus(ActionStatus.ACCEPTED);
        event.setMessage("STARTED");
        event.setOccurredAt(Instant.parse("2026-01-01T00:00:00Z"));
        event.setData(Map.of(
                "actionType", "integration.orchestration.start",
                "source", "AGENT",
                "actor", Map.of("type", "AGENT", "id", "agent-1", "tenantId", "tenant-a"),
                "target", Map.of("type", "OPENAPI_ORCHESTRATION", "id", "orch-1",
                        "tenantId", "tenant-a", "ownerService", "service-openapi"),
                "ownerService", "service-openapi",
                "traceId", "trace-3",
                "correlationId", "corr-3"));
        return event;
    }
}
