package com.triobase.service.lowcode.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionEventType;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionEventPayload;
import com.triobase.service.lowcode.entity.LowcodeActionAuditEvent;
import com.triobase.service.lowcode.mapper.LowcodeActionAuditEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LowcodeOwnerActionAuditSinkTest {

    private final LowcodeActionAuditEventMapper mapper = mock(LowcodeActionAuditEventMapper.class);
    private final LowcodeOwnerActionAuditSink sink = new LowcodeOwnerActionAuditSink(
            mapper,
            new ObjectMapper().findAndRegisterModules());

    @Test
    void persistsActionAuditEventWithCorrelationFields() {
        sink.emit(event());

        ArgumentCaptor<LowcodeActionAuditEvent> captor = ArgumentCaptor.forClass(LowcodeActionAuditEvent.class);
        verify(mapper).insert(captor.capture());
        LowcodeActionAuditEvent audit = captor.getValue();
        assertThat(audit.getActionId()).isEqualTo("act_1");
        assertThat(audit.getActionType()).isEqualTo("lowcode.form.submit");
        assertThat(audit.getTargetType()).isEqualTo("LOWCODE_FORM_INSTANCE");
        assertThat(audit.getTargetId()).isEqualTo("form-instance-1");
        assertThat(audit.getTraceId()).isEqualTo("trace-1");
        assertThat(audit.getCorrelationId()).isEqualTo("corr-1");
        assertThat(audit.getEventDataJson()).contains("\"secret\":\"[REDACTED]\"");
    }

    private ActionEventPayload event() {
        ActionEventPayload event = new ActionEventPayload();
        event.setEventId("evt_1");
        event.setActionId("act_1");
        event.setEventType(ActionEventType.SUCCEEDED);
        event.setStatus(ActionStatus.SUCCEEDED);
        event.setMessage("OK");
        event.setOccurredAt(Instant.parse("2026-01-01T00:00:00Z"));
        event.setData(Map.of(
                "actionType", "lowcode.form.submit",
                "source", "LUI",
                "actor", Map.of("type", "USER", "id", "user-1", "displayName", "Alice", "tenantId", "tenant-a"),
                "target", Map.of("type", "LOWCODE_FORM_INSTANCE", "id", "form-instance-1",
                        "tenantId", "tenant-a", "ownerService", "service-lowcode"),
                "ownerService", "service-lowcode",
                "traceId", "trace-1",
                "correlationId", "corr-1",
                "payloadSummary", Map.of("secret", "[REDACTED]")));
        return event;
    }
}
