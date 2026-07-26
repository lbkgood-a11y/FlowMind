package com.triobase.service.openapi.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionEventType;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.core.util.StringHelpers;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionEventPayload;
import com.triobase.common.action.runtime.OwnerActionAuditSink;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.dto.catalog.BusinessTimelineEventRecord;
import com.triobase.service.openapi.domain.entity.AuditEvent;
import com.triobase.service.openapi.infrastructure.mapper.AuditEventMapper;
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
public class OpenApiOwnerActionAuditSink implements OwnerActionAuditSink {

    private static final Logger log = LoggerFactory.getLogger(OpenApiOwnerActionAuditSink.class);

    private final AuditEventMapper mapper;
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
        AuditEvent audit = new AuditEvent();
        audit.setId(UlidGenerator.nextUlid());
        audit.setTenantId(StringHelpers.firstNonBlank(string(actor.get("tenantId")), string(target.get("tenantId"))));
        audit.setActorId(StringHelpers.firstNonBlank(string(actor.get("id")), "SYSTEM"));
        audit.setActorType(auditActorType(string(actor.get("type"))));
        audit.setAction(event.getEventType() != null ? event.getEventType().name() : "OWNER_ACTION_EVENT");
        audit.setResourceType(StringHelpers.firstNonBlank(string(target.get("type")), "GLOBAL_ACTION"));
        audit.setResourceId(StringHelpers.firstNonBlank(string(target.get("id")), event.getActionId()));
        audit.setOutcome(outcome(event));
        audit.setReason(event.getMessage());
        audit.setTraceId(string(data.get("traceId")));
        audit.setActionId(event.getActionId());
        audit.setActionType(string(data.get("actionType")));
        audit.setActionSource(string(data.get("source")));
        audit.setActionActorType(string(actor.get("type")));
        audit.setActionActorId(string(actor.get("id")));
        audit.setActionActorName(string(actor.get("displayName")));
        audit.setActionTraceId(string(data.get("traceId")));
        audit.setActionCorrelationId(string(data.get("correlationId")));
        audit.setChangeSummary(summary(data));
        audit.setCreatedAt(toLocalDateTime(event.getOccurredAt()));
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
            record.setTargetType(StringHelpers.firstNonBlank(string(target.get("type")), "GLOBAL_ACTION"));
            record.setTargetId(StringHelpers.firstNonBlank(string(target.get("id")), event.getActionId()));
            record.setEventType(event.getEventType() != null ? event.getEventType().name() : "OWNER_ACTION_EVENT");
            record.setDisplayName(event.getMessage());
            record.setActorId(StringHelpers.firstNonBlank(string(actor.get("id")), "SYSTEM"));
            record.setActorName(string(actor.get("displayName")));
            record.setActionId(event.getActionId());
            record.setActionType(string(data.get("actionType")));
            record.setActionStatus(event.getStatus() != null ? event.getStatus().name() : null);
            record.setOwnerService("service-openapi");
            record.setOwnerExecutionRef(null);
            record.setTraceId(string(data.get("traceId")));
            record.setCorrelationId(string(data.get("correlationId")));
            record.setOccurredAt(event.getOccurredAt());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(InternalServiceTokenFilter.HEADER_SERVICE_NAME, "service-openapi");
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

    private String outcome(ActionEventPayload event) {
        ActionStatus status = event.getStatus();
        if (status == ActionStatus.REJECTED || event.getEventType() == ActionEventType.AUTHORIZATION_DENIED) {
            return "DENIED";
        }
        if (status == ActionStatus.FAILED || event.getEventType() == ActionEventType.FAILED
                || event.getEventType() == ActionEventType.VALIDATION_FAILED) {
            return "FAILED";
        }
        return "SUCCESS";
    }

    private JsonNode summary(Map<String, Object> data) {
        return objectMapper.valueToTree(data);
    }

    private String auditActorType(String actionActorType) {
        if ("USER".equals(actionActorType)) {
            return "USER";
        }
        if ("AGENT".equals(actionActorType) || "SERVICE".equals(actionActorType)
                || "SCHEDULER".equals(actionActorType) || "WORKFLOW".equals(actionActorType)) {
            return "APPLICATION";
        }
        return "SYSTEM";
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant != null ? instant : Instant.now(), ZoneOffset.UTC);
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
