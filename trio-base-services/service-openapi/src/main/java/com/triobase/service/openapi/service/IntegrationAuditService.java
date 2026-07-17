package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.domain.entity.AuditEvent;
import com.triobase.service.openapi.infrastructure.mapper.AuditEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class IntegrationAuditService {

    private final AuditEventMapper auditEventMapper;

    public void success(String action, String resourceType, String resourceId, JsonNode summary) {
        record(action, resourceType, resourceId, "SUCCESS", null, summary);
    }

    public void denied(String action, String resourceType, String resourceId, String reason) {
        record(action, resourceType, resourceId, "DENIED", reason, JsonNodeFactory.instance.objectNode());
    }

    public void failure(String action, String resourceType, String resourceId, String reason, JsonNode summary) {
        record(action, resourceType, resourceId, "FAILED", reason, summary);
    }

    private void record(
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            String reason,
            JsonNode summary) {
        AuditEvent event = new AuditEvent();
        event.setId(UlidGenerator.nextUlid());
        event.setTenantId(SecurityContextHolder.getTenantId());
        event.setActorId(StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM");
        event.setActorType(SecurityContextHolder.getUserId() == null ? "SYSTEM" : "USER");
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setOutcome(outcome);
        event.setReason(reason);
        event.setTraceId(TraceUtil.getTraceId());
        event.setChangeSummary(summary == null ? JsonNodeFactory.instance.objectNode() : summary.deepCopy());
        event.setCreatedAt(LocalDateTime.now());
        auditEventMapper.insert(event);
    }
}
