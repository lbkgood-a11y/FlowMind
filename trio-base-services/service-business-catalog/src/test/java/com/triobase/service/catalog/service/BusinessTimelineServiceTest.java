package com.triobase.service.catalog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.catalog.BusinessTimelineEntry;
import com.triobase.common.dto.catalog.BusinessTimelineQuery;
import com.triobase.service.catalog.entity.BusinessTimelineEvent;
import com.triobase.service.catalog.mapper.BusinessTimelineEventMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessTimelineServiceTest {

    private final BusinessTimelineEventMapper timelineMapper = mock(BusinessTimelineEventMapper.class);
    private final BusinessTimelineService service = new BusinessTimelineService(
            timelineMapper,
            new ObjectMapper().findAndRegisterModules());

    @Test
    void queryReturnsTimelineEventsFromSingleTable() {
        Page<BusinessTimelineEvent> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of(timelineEvent()));
        mpPage.setTotal(1);
        when(timelineMapper.selectPage(any(), any())).thenReturn(mpPage);

        PageResult<BusinessTimelineEntry> page = service.query(query());

        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getTotal()).isEqualTo(1);
        BusinessTimelineEntry entry = page.getRecords().getFirst();
        assertThat(entry.getEventSource()).isEqualTo("OWNER_ACTION_EVENT");
        assertThat(entry.getActionId()).isEqualTo("act_1");
        assertThat(entry.getTargetType()).isEqualTo("LOWCODE_FORM_INSTANCE");
        assertThat(entry.getTargetId()).isEqualTo("form-instance-1");
        assertThat(entry.getTraceId()).isEqualTo("trace-1");
        assertThat(entry.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void queryReturnsEmptyPageWhenNoMatches() {
        Page<BusinessTimelineEvent> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of());
        mpPage.setTotal(0);
        when(timelineMapper.selectPage(any(), any())).thenReturn(mpPage);

        PageResult<BusinessTimelineEntry> page = service.query(query());

        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getTotal()).isZero();
    }

    private BusinessTimelineQuery query() {
        BusinessTimelineQuery query = new BusinessTimelineQuery();
        query.setTenantId("tenant-a");
        query.setActionId("act_1");
        query.setTargetType("LOWCODE_FORM_INSTANCE");
        query.setTargetId("form-instance-1");
        query.setTraceId("trace-1");
        query.setCorrelationId("corr-1");
        query.setEventSource("OWNER_ACTION_EVENT");
        return query;
    }

    private BusinessTimelineEvent timelineEvent() {
        BusinessTimelineEvent event = new BusinessTimelineEvent();
        event.setId("row-1");
        event.setEventSource("OWNER_ACTION_EVENT");
        event.setTenantId("tenant-a");
        event.setActionId("act_1");
        event.setActionType("lowcode.form.submit");
        event.setEventType("SUCCEEDED");
        event.setActionStatus("SUCCEEDED");
        event.setActorId("user-1");
        event.setActorName("Alice");
        event.setTargetType("LOWCODE_FORM_INSTANCE");
        event.setTargetId("form-instance-1");
        event.setOwnerService("service-lowcode");
        event.setTraceId("trace-1");
        event.setCorrelationId("corr-1");
        event.setDisplayName("OK");
        event.setSummaryJson("{\"traceId\":\"trace-1\",\"correlationId\":\"corr-1\"}");
        event.setOccurredAt(LocalDateTime.parse("2026-01-01T00:00:00"));
        return event;
    }
}
