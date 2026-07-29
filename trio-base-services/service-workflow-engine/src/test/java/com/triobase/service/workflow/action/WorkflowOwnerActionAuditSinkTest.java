package com.triobase.service.workflow.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionEventType;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionEventPayload;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.service.workflow.entity.WorkflowActionAuditEvent;
import com.triobase.service.workflow.mapper.WorkflowActionAuditEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorkflowOwnerActionAuditSinkTest {

    private final WorkflowActionAuditEventMapper mapper = mock(WorkflowActionAuditEventMapper.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final InternalServiceSecurityProperties securityProperties = new InternalServiceSecurityProperties();
    private final WorkflowOwnerActionAuditSink sink = new WorkflowOwnerActionAuditSink(
            mapper,
            new ObjectMapper().findAndRegisterModules(),
            restTemplate,
            securityProperties);

    @Test
    void persistsActionAuditEventWithTraceAndTarget() {
        sink.emit(event());

        ArgumentCaptor<WorkflowActionAuditEvent> captor = ArgumentCaptor.forClass(WorkflowActionAuditEvent.class);
        verify(mapper).insert(captor.capture());
        WorkflowActionAuditEvent audit = captor.getValue();
        assertThat(audit.getActionId()).isEqualTo("act_2");
        assertThat(audit.getActionType()).isEqualTo("process.task.approve");
        assertThat(audit.getTargetType()).isEqualTo("PROCESS_TASK");
        assertThat(audit.getTargetId()).isEqualTo("task-1");
        assertThat(audit.getTraceId()).isEqualTo("trace-2");
        assertThat(audit.getCorrelationId()).isEqualTo("corr-2");
    }

    private ActionEventPayload event() {
        ActionEventPayload event = new ActionEventPayload();
        event.setEventId("evt_2");
        event.setActionId("act_2");
        event.setEventType(ActionEventType.SUCCEEDED);
        event.setStatus(ActionStatus.SUCCEEDED);
        event.setMessage("APPROVED");
        event.setOccurredAt(Instant.parse("2026-01-01T00:00:00Z"));
        event.setData(Map.of(
                "actionType", "process.task.approve",
                "source", "GUI",
                "actor", Map.of("type", "USER", "id", "user-2", "displayName", "Bob", "tenantId", "tenant-a"),
                "target", Map.of("type", "PROCESS_TASK", "id", "task-1",
                        "tenantId", "tenant-a", "ownerService", "service-workflow-engine"),
                "ownerService", "service-workflow-engine",
                "traceId", "trace-2",
                "correlationId", "corr-2"));
        return event;
    }
}
