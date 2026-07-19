package com.triobase.service.action.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionEventType;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.catalog.BusinessTimelineEntry;
import com.triobase.common.dto.catalog.BusinessTimelineEventRecord;
import com.triobase.common.dto.catalog.BusinessTimelineQuery;
import com.triobase.service.action.entity.ActionEvent;
import com.triobase.service.action.entity.ActionExecution;
import com.triobase.service.action.entity.DocumentTimelineEvent;
import com.triobase.service.action.exception.ActionRuntimeException;
import com.triobase.service.action.mapper.ActionEventMapper;
import com.triobase.service.action.mapper.ActionExecutionMapper;
import com.triobase.service.action.mapper.DocumentTimelineEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentTimelineServiceTest {

    private DocumentTimelineEventMapper timelineEventMapper;
    private ActionExecutionMapper actionExecutionMapper;
    private ActionEventMapper actionEventMapper;
    private DocumentTimelineService service;

    @BeforeEach
    void setUp() {
        timelineEventMapper = mock(DocumentTimelineEventMapper.class);
        actionExecutionMapper = mock(ActionExecutionMapper.class);
        actionEventMapper = mock(ActionEventMapper.class);
        service = new DocumentTimelineService(
                timelineEventMapper,
                actionExecutionMapper,
                actionEventMapper,
                new ObjectMapper());
    }

    @Test
    void recordsBoundedDomainEventWithRedactedSummary() {
        BusinessTimelineEventRecord request = new BusinessTimelineEventRecord();
        request.setTenantId("tenant-a");
        request.setTargetType("SCM_PURCHASE_ORDER");
        request.setTargetId("PO-001");
        request.setEventType("DRAFT_UPDATED");
        request.setDisplayName("保存草稿");
        request.setActorId("U001");
        request.setOccurredAt(Instant.parse("2026-07-19T01:00:00Z"));
        request.setSummary(Map.of(
                "changedFields", List.of("amount", "phone"),
                "phone", "13800000000",
                "nested", Map.of("accessToken", "raw-token")));

        BusinessTimelineEntry entry = service.record(request);

        assertThat(entry.isRedacted()).isTrue();
        assertThat(entry.getSummary()).containsEntry("phone", "[REDACTED]");
        assertThat(String.valueOf(entry.getSummary().get("nested")))
                .contains("accessToken=[REDACTED]");
        ArgumentCaptor<DocumentTimelineEvent> captor = ArgumentCaptor.forClass(DocumentTimelineEvent.class);
        verify(timelineEventMapper).insert(captor.capture());
        assertThat(captor.getValue().getSummaryJson()).doesNotContain("13800000000", "raw-token");
    }

    @Test
    void queriesTimelineByTargetAndCorrelatesActionEvents() {
        DocumentTimelineEvent domain = new DocumentTimelineEvent();
        domain.setId("evt-domain");
        domain.setEventSource("DOMAIN_EVENT");
        domain.setTenantId("tenant-a");
        domain.setTargetType("SCM_PURCHASE_ORDER");
        domain.setTargetId("PO-001");
        domain.setEventType("STATUS_CHANGED");
        domain.setDisplayName("提交审批");
        domain.setActionId("act-1");
        domain.setActionType("scm.purchaseOrder.submit");
        domain.setTraceId("trace-1");
        domain.setSummaryJson("{\"newStatus\":\"SUBMITTED\"}");
        domain.setRedacted(true);
        domain.setOccurredAt(LocalDateTime.ofInstant(Instant.parse("2026-07-19T01:02:00Z"), ZoneOffset.UTC));
        when(timelineEventMapper.selectList(any())).thenReturn(List.of(domain));

        ActionExecution execution = new ActionExecution();
        execution.setId("act-1");
        execution.setTenantId("tenant-a");
        execution.setActionType("scm.purchaseOrder.submit");
        execution.setStatus("SUCCEEDED");
        execution.setTargetType("SCM_PURCHASE_ORDER");
        execution.setTargetId("PO-001");
        execution.setActorId("U001");
        execution.setOwnerService("service-scm");
        execution.setOwnerExecutionRef("wf-1");
        execution.setTraceId("trace-1");
        execution.setCorrelationId("corr-1");
        execution.setPayloadSummary("{\"phone\":\"13800000000\"}");
        execution.setUpdatedAt(LocalDateTime.ofInstant(Instant.parse("2026-07-19T01:01:00Z"), ZoneOffset.UTC));
        when(actionExecutionMapper.selectList(any())).thenReturn(List.of(execution));

        ActionEvent actionEvent = new ActionEvent();
        actionEvent.setId("evt-action");
        actionEvent.setActionId("act-1");
        actionEvent.setTenantId("tenant-a");
        actionEvent.setEventType(ActionEventType.SUCCEEDED.name());
        actionEvent.setStatus("SUCCEEDED");
        actionEvent.setSequenceNo(3);
        actionEvent.setMessage("ACTION_SUCCEEDED");
        actionEvent.setTraceId("trace-1");
        actionEvent.setEventDataJson("{\"credential\":\"secret\"}");
        actionEvent.setOccurredAt(LocalDateTime.ofInstant(Instant.parse("2026-07-19T01:03:00Z"), ZoneOffset.UTC));
        when(actionEventMapper.selectList(any())).thenReturn(List.of(actionEvent));

        BusinessTimelineQuery query = new BusinessTimelineQuery();
        query.setTenantId("tenant-a");
        query.setTargetType("SCM_PURCHASE_ORDER");
        query.setTargetId("PO-001");
        query.setTraceId("trace-1");

        PageResult<BusinessTimelineEntry> result = service.query(query);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getRecords())
                .extracting(BusinessTimelineEntry::getEventSource)
                .containsExactly("ACTION_EVENT", "DOMAIN_EVENT", "GLOBAL_ACTION");
        assertThat(result.getRecords().getFirst().getSummary().toString()).doesNotContain("secret");
        assertThat(result.getRecords().get(2).getSummary().toString()).doesNotContain("13800000000");
    }

    @Test
    void rejectsTimelineQueryWithoutTenantBoundary() {
        BusinessTimelineQuery query = new BusinessTimelineQuery();
        query.setTargetType("SCM_PURCHASE_ORDER");
        query.setTargetId("PO-001");

        assertThatThrownBy(() -> service.query(query))
                .isInstanceOf(ActionRuntimeException.class)
                .hasMessage("DOCUMENT_TIMELINE_TENANT_REQUIRED");
    }
}
