package com.triobase.service.workflow.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.model.ActionEventPayload;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.core.util.StringHelpers;
import com.triobase.common.action.runtime.OwnerActionAuditSink;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.dto.catalog.BusinessTimelineEventRecord;
import com.triobase.service.workflow.entity.WorkflowActionAuditEvent;
import com.triobase.service.workflow.mapper.WorkflowActionAuditEventMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkflowOwnerActionAuditSink implements OwnerActionAuditSink {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOwnerActionAuditSink.class);

    private final WorkflowActionAuditEventMapper mapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final InternalServiceSecurityProperties internalProperties;

    @Value("${triobase.internal.business-catalog-base-url:http://service-business-catalog:8090}")
    private String businessCatalogBaseUrl;

    @Override
    public void emit(ActionEventPayload event) {
        if (event == null || event.getData() == null) {
            return;
        }
        Map<String, Object> data = event.getData();
        Map<String, Object> actor = map(data.get("actor"));
        Map<String, Object> target = map(data.get("target"));
        WorkflowActionAuditEvent audit = new WorkflowActionAuditEvent();
        audit.setId(UlidGenerator.nextUlid());
        audit.setEventId(event.getEventId());
        audit.setTenantId(StringHelpers.firstNonBlank(string(actor.get("tenantId")), string(target.get("tenantId"))));
        audit.setActionId(event.getActionId());
        audit.setActionType(string(data.get("actionType")));
        audit.setEventType(event.getEventType() != null ? event.getEventType().name() : null);
        audit.setActionStatus(event.getStatus() != null ? event.getStatus().name() : string(data.get("status")));
        audit.setActionSource(string(data.get("source")));
        audit.setActorType(string(actor.get("type")));
        audit.setActorId(string(actor.get("id")));
        audit.setActorName(string(actor.get("displayName")));
        audit.setTargetType(string(target.get("type")));
        audit.setTargetId(string(target.get("id")));
        audit.setTargetOwnerService(string(target.get("ownerService")));
        audit.setOwnerService(string(data.get("ownerService")));
        audit.setIdempotencyKey(string(data.get("idempotencyKey")));
        audit.setTraceId(string(data.get("traceId")));
        audit.setCorrelationId(string(data.get("correlationId")));
        audit.setOwnerExecutionRef(string(data.get("ownerExecutionRef")));
        audit.setMessage(event.getMessage());
        audit.setEventDataJson(toJson(data));
        audit.setOccurredAt(toLocalDateTime(event.getOccurredAt()));
        mapper.insert(audit);
        pushToTimeline(event);
    }

    private void pushToTimeline(ActionEventPayload event) {
        try {
            Map<String, Object> data = event.getData();
            Map<String, Object> actor = map(data.get("actor"));
            Map<String, Object> target = map(data.get("target"));
            BusinessTimelineEventRecord record = new BusinessTimelineEventRecord();
            record.setEventId(event.getEventId());
            record.setEventSource("OWNER_ACTION_EVENT");
            record.setTenantId(StringHelpers.firstNonBlank(string(actor.get("tenantId")), string(target.get("tenantId"))));
            record.setTargetType(string(target.get("type")));
            record.setTargetId(string(target.get("id")));
            record.setEventType(event.getEventType() != null ? event.getEventType().name() : null);
            record.setDisplayName(event.getMessage());
            record.setActorId(string(actor.get("id")));
            record.setActorName(string(actor.get("displayName")));
            record.setActionId(event.getActionId());
            record.setActionType(string(data.get("actionType")));
            record.setActionStatus(event.getStatus() != null ? event.getStatus().name() : null);
            record.setOwnerService(string(data.get("ownerService")));
            record.setOwnerExecutionRef(string(data.get("ownerExecutionRef")));
            record.setTraceId(string(data.get("traceId")));
            record.setCorrelationId(string(data.get("correlationId")));
            record.setOccurredAt(event.getOccurredAt());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(InternalServiceTokenFilter.HEADER_SERVICE_NAME, "service-workflow-engine");
            headers.set(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, internalProperties.getToken());
            restTemplate.postForEntity(
                    businessCatalogBaseUrl + "/internal/v1/business-timeline/events",
                    new HttpEntity<>(record, headers), Void.class);
        } catch (Exception exception) {
            log.warn("Failed to push timeline event to business-catalog (actionId={}): {}",
                    event.getActionId(), exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> actual ? (Map<String, Object>) actual : Map.of();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant != null ? instant : Instant.now(), ZoneOffset.UTC);
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
