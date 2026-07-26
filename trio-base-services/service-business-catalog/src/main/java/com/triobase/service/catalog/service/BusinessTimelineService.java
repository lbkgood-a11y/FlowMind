package com.triobase.service.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.util.StringHelpers;
import com.triobase.common.dto.catalog.BusinessTimelineEntry;
import com.triobase.common.dto.catalog.BusinessTimelineEventRecord;
import com.triobase.common.dto.catalog.BusinessTimelineQuery;
import com.triobase.service.catalog.entity.BusinessTimelineEvent;
import com.triobase.service.catalog.mapper.BusinessTimelineEventMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BusinessTimelineService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final Logger log = LoggerFactory.getLogger(BusinessTimelineService.class);

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "credential", "accesskey", "privatekey",
            "idcard", "idnumber", "identityno", "phone", "mobile", "telephone", "bankaccount");

    private final BusinessTimelineEventMapper timelineEventMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public BusinessTimelineEntry record(BusinessTimelineEventRecord request) {
        validateRecord(request);
        RedactionResult redacted = redact(request.getSummary());
        BusinessTimelineEvent event = new BusinessTimelineEvent();
        event.setId(StringHelpers.firstNonBlank(request.getEventId(), UlidGenerator.nextUlid()));
        event.setEventSource(StringHelpers.firstNonBlank(request.getEventSource(), "DOMAIN_EVENT"));
        event.setTenantId(request.getTenantId().trim());
        event.setTargetType(request.getTargetType().trim());
        event.setTargetId(request.getTargetId().trim());
        event.setEventType(request.getEventType().trim());
        event.setDisplayName(request.getDisplayName());
        event.setActorId(request.getActorId());
        event.setActorName(request.getActorName());
        event.setActionId(request.getActionId());
        event.setActionType(request.getActionType());
        event.setActionStatus(request.getActionStatus());
        event.setOwnerService(request.getOwnerService());
        event.setOwnerExecutionRef(request.getOwnerExecutionRef());
        event.setTraceId(request.getTraceId());
        event.setCorrelationId(request.getCorrelationId());
        event.setSummaryJson(toJson(redacted.summary()));
        event.setRedacted(redacted.redacted());
        event.setOccurredAt(request.getOccurredAt() != null
                ? toLocalDateTime(request.getOccurredAt())
                : LocalDateTime.now(ZoneOffset.UTC));
        timelineEventMapper.insert(event);
        return toEntry(event, event.getEventSource());
    }

    public PageResult<BusinessTimelineEntry> query(BusinessTimelineQuery query) {
        BusinessTimelineQuery actual = query != null ? query : new BusinessTimelineQuery();
        validateQuery(actual);
        int page = Math.max(1, actual.getPage());
        int size = Math.max(1, Math.min(actual.getSize(), MAX_PAGE_SIZE));

        Page<BusinessTimelineEvent> mpPage = new Page<>(page, size);
        LambdaQueryWrapper<BusinessTimelineEvent> wrapper =
                new LambdaQueryWrapper<BusinessTimelineEvent>()
                        .eq(BusinessTimelineEvent::getTenantId, actual.getTenantId())
                        .eq(hasText(actual.getEventSource()), BusinessTimelineEvent::getEventSource,
                                actual.getEventSource())
                        .eq(hasText(actual.getTargetType()), BusinessTimelineEvent::getTargetType,
                                actual.getTargetType())
                        .eq(hasText(actual.getTargetId()), BusinessTimelineEvent::getTargetId,
                                actual.getTargetId())
                        .eq(hasText(actual.getActionId()), BusinessTimelineEvent::getActionId,
                                actual.getActionId())
                        .eq(hasText(actual.getActionType()), BusinessTimelineEvent::getActionType,
                                actual.getActionType())
                        .eq(hasText(actual.getActionStatus()), BusinessTimelineEvent::getActionStatus,
                                actual.getActionStatus())
                        .eq(hasText(actual.getActorId()), BusinessTimelineEvent::getActorId,
                                actual.getActorId())
                        .eq(hasText(actual.getEventType()), BusinessTimelineEvent::getEventType,
                                actual.getEventType())
                        .eq(hasText(actual.getTraceId()), BusinessTimelineEvent::getTraceId,
                                actual.getTraceId())
                        .eq(hasText(actual.getCorrelationId()), BusinessTimelineEvent::getCorrelationId,
                                actual.getCorrelationId())
                        .eq(hasText(actual.getOwnerExecutionRef()), BusinessTimelineEvent::getOwnerExecutionRef,
                                actual.getOwnerExecutionRef())
                        .ge(actual.getStartTime() != null, BusinessTimelineEvent::getOccurredAt,
                                toLocalDateTime(actual.getStartTime()))
                        .le(actual.getEndTime() != null, BusinessTimelineEvent::getOccurredAt,
                                toLocalDateTime(actual.getEndTime()))
                        .orderByDesc(BusinessTimelineEvent::getOccurredAt);

        Page<BusinessTimelineEvent> result = timelineEventMapper.selectPage(mpPage, wrapper);
        List<BusinessTimelineEntry> entries = result.getRecords().stream()
                .map(event -> toEntry(event, event.getEventSource()))
                .toList();
        return PageResult.of(entries, (int) result.getTotal(), page, size);
    }

    private BusinessTimelineEntry toEntry(BusinessTimelineEvent event, String source) {
        BusinessTimelineEntry entry = new BusinessTimelineEntry();
        entry.setEventId(event.getId());
        entry.setEventSource(source);
        entry.setTenantId(event.getTenantId());
        entry.setTargetType(event.getTargetType());
        entry.setTargetId(event.getTargetId());
        entry.setEventType(event.getEventType());
        entry.setDisplayName(event.getDisplayName());
        entry.setActorId(event.getActorId());
        entry.setActorName(event.getActorName());
        entry.setActionId(event.getActionId());
        entry.setActionType(event.getActionType());
        entry.setActionStatus(event.getActionStatus());
        entry.setOwnerService(event.getOwnerService());
        entry.setOwnerExecutionRef(event.getOwnerExecutionRef());
        entry.setTraceId(event.getTraceId());
        entry.setCorrelationId(event.getCorrelationId());
        entry.setOccurredAt(toInstant(event.getOccurredAt()));
        entry.setRedacted(Boolean.TRUE.equals(event.getRedacted()));
        entry.setSummary(fromJson(event.getSummaryJson()));
        return entry;
    }

    private void validateRecord(BusinessTimelineEventRecord request) {
        if (request == null
                || !hasText(request.getTenantId())
                || !hasText(request.getTargetType())
                || !hasText(request.getTargetId())
                || !hasText(request.getEventType())) {
            throw new BizException(40060, "DOCUMENT_TIMELINE_EVENT_REQUIRED");
        }
    }

    private void validateQuery(BusinessTimelineQuery query) {
        if (!hasText(query.getTenantId())) {
            throw new BizException(40360, "DOCUMENT_TIMELINE_TENANT_REQUIRED");
        }
        if (!hasText(query.getTargetType())
                && !hasText(query.getTargetId())
                && !hasText(query.getActionId())
                && !hasText(query.getTraceId())
                && !hasText(query.getCorrelationId())
                && !hasText(query.getOwnerExecutionRef())) {
            throw new BizException(40061, "DOCUMENT_TIMELINE_FILTER_REQUIRED");
        }
    }

    private RedactionResult redact(Map<String, Object> summary) {
        Map<String, Object> source = summary != null ? summary : Map.of();
        Map<String, Object> target = new LinkedHashMap<>();
        boolean redacted = false;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (sensitiveKey(entry.getKey())) {
                target.put(entry.getKey(), "[REDACTED]");
                redacted = true;
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                RedactionResult nestedResult = redact(toStringKeyMap(nested));
                target.put(entry.getKey(), nestedResult.summary());
                redacted = redacted || nestedResult.redacted();
            } else {
                target.put(entry.getKey(), entry.getValue());
            }
        }
        return new RedactionResult(target, redacted);
    }

    private boolean sensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalized = key.replace("_", "").replace("-", "").toLowerCase();
        return SENSITIVE_KEYS.contains(normalized);
    }

    private Map<String, Object> safeJsonOrText(String value) {
        if (!hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            log.warn("Failed to parse JSON in timeline field, using raw text fallback: {}", exception.getMessage());
            return Map.of("text", value);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value != null ? value : Map.of());
        } catch (Exception exception) {
            log.warn("Failed to serialize timeline summary to JSON: {}", exception.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String value) {
        if (!hasText(value)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            log.warn("Failed to parse timeline summary JSON: {}", exception.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("text", value);
            return fallback;
        }
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private Instant toInstant(LocalDateTime value) {
        return value != null ? value.toInstant(ZoneOffset.UTC) : null;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private record RedactionResult(Map<String, Object> summary, boolean redacted) {
    }
}
