package com.triobase.service.action.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.core.id.UlidGenerator;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentTimelineService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final List<String> SENSITIVE_KEY_FRAGMENTS = List.of(
            "password",
            "secret",
            "token",
            "credential",
            "idcard",
            "identity",
            "phone",
            "mobile",
            "bank",
            "account");

    private final DocumentTimelineEventMapper timelineEventMapper;
    private final ActionExecutionMapper actionExecutionMapper;
    private final ActionEventMapper actionEventMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public BusinessTimelineEntry record(BusinessTimelineEventRecord request) {
        validateRecord(request);
        RedactionResult redacted = redact(request.getSummary());
        DocumentTimelineEvent event = new DocumentTimelineEvent();
        event.setId(firstNonBlank(request.getEventId(), UlidGenerator.nextUlid()));
        event.setEventSource(firstNonBlank(request.getEventSource(), "DOMAIN_EVENT"));
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
        event.setOccurredAt(toLocalDateTime(request.getOccurredAt()));
        timelineEventMapper.insert(event);
        return toEntry(event);
    }

    public PageResult<BusinessTimelineEntry> query(BusinessTimelineQuery query) {
        BusinessTimelineQuery actual = query != null ? query : new BusinessTimelineQuery();
        validateQuery(actual);
        List<BusinessTimelineEntry> entries = new ArrayList<>();
        if (includeSource(actual, "DOMAIN_EVENT")) {
            entries.addAll(domainEntries(actual));
        }
        if (includeSource(actual, "GLOBAL_ACTION")) {
            entries.addAll(actionEntries(actual));
        }
        entries.sort(Comparator
                .comparing(BusinessTimelineEntry::getOccurredAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(BusinessTimelineEntry::getSequenceNo,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        int page = Math.max(1, actual.getPage());
        int size = Math.max(1, Math.min(actual.getSize(), MAX_PAGE_SIZE));
        int from = Math.min((page - 1) * size, entries.size());
        int to = Math.min(from + size, entries.size());
        return PageResult.of(entries.subList(from, to), entries.size(), page, size);
    }

    private List<BusinessTimelineEntry> domainEntries(BusinessTimelineQuery query) {
        return timelineEventMapper.selectList(new LambdaQueryWrapper<DocumentTimelineEvent>()
                        .eq(DocumentTimelineEvent::getTenantId, query.getTenantId())
                        .eq(hasText(query.getTargetType()), DocumentTimelineEvent::getTargetType, query.getTargetType())
                        .eq(hasText(query.getTargetId()), DocumentTimelineEvent::getTargetId, query.getTargetId())
                        .eq(hasText(query.getActionId()), DocumentTimelineEvent::getActionId, query.getActionId())
                        .eq(hasText(query.getActionType()), DocumentTimelineEvent::getActionType, query.getActionType())
                        .eq(hasText(query.getActionStatus()), DocumentTimelineEvent::getActionStatus, query.getActionStatus())
                        .eq(hasText(query.getActorId()), DocumentTimelineEvent::getActorId, query.getActorId())
                        .eq(hasText(query.getEventType()), DocumentTimelineEvent::getEventType, query.getEventType())
                        .eq(hasText(query.getTraceId()), DocumentTimelineEvent::getTraceId, query.getTraceId())
                        .eq(hasText(query.getCorrelationId()), DocumentTimelineEvent::getCorrelationId, query.getCorrelationId())
                        .eq(hasText(query.getOwnerExecutionRef()), DocumentTimelineEvent::getOwnerExecutionRef,
                                query.getOwnerExecutionRef())
                        .ge(query.getStartTime() != null, DocumentTimelineEvent::getOccurredAt,
                                toLocalDateTime(query.getStartTime()))
                        .le(query.getEndTime() != null, DocumentTimelineEvent::getOccurredAt,
                                toLocalDateTime(query.getEndTime()))
                        .orderByDesc(DocumentTimelineEvent::getOccurredAt)
                        .last("LIMIT " + MAX_PAGE_SIZE * 5))
                .stream()
                .map(this::toEntry)
                .toList();
    }

    private List<BusinessTimelineEntry> actionEntries(BusinessTimelineQuery query) {
        List<ActionExecution> executions = actionExecutionMapper.selectList(
                new LambdaQueryWrapper<ActionExecution>()
                        .eq(ActionExecution::getTenantId, query.getTenantId())
                        .eq(hasText(query.getTargetType()), ActionExecution::getTargetType, query.getTargetType())
                        .eq(hasText(query.getTargetId()), ActionExecution::getTargetId, query.getTargetId())
                        .eq(hasText(query.getActionId()), ActionExecution::getId, query.getActionId())
                        .eq(hasText(query.getActionType()), ActionExecution::getActionType, query.getActionType())
                        .eq(hasText(query.getActionStatus()), ActionExecution::getStatus, query.getActionStatus())
                        .eq(hasText(query.getActorId()), ActionExecution::getActorId, query.getActorId())
                        .eq(hasText(query.getTraceId()), ActionExecution::getTraceId, query.getTraceId())
                        .eq(hasText(query.getCorrelationId()), ActionExecution::getCorrelationId, query.getCorrelationId())
                        .eq(hasText(query.getOwnerExecutionRef()), ActionExecution::getOwnerExecutionRef,
                                query.getOwnerExecutionRef())
                        .ge(query.getStartTime() != null, ActionExecution::getCreatedAt,
                                toLocalDateTime(query.getStartTime()))
                        .le(query.getEndTime() != null, ActionExecution::getCreatedAt,
                                toLocalDateTime(query.getEndTime()))
                        .orderByDesc(ActionExecution::getUpdatedAt)
                        .last("LIMIT " + MAX_PAGE_SIZE * 5));
        if (executions.isEmpty()) {
            return List.of();
        }
        Map<String, ActionExecution> byId = executions.stream()
                .collect(Collectors.toMap(ActionExecution::getId, Function.identity(), (first, second) -> first));
        List<BusinessTimelineEntry> entries = executions.stream()
                .map(this::toExecutionEntry)
                .collect(Collectors.toCollection(ArrayList::new));
        List<ActionEvent> events = actionEventMapper.selectList(new LambdaQueryWrapper<ActionEvent>()
                .in(ActionEvent::getActionId, byId.keySet())
                .eq(hasText(query.getEventType()), ActionEvent::getEventType, query.getEventType())
                .eq(hasText(query.getTraceId()), ActionEvent::getTraceId, query.getTraceId())
                .ge(query.getStartTime() != null, ActionEvent::getOccurredAt, toLocalDateTime(query.getStartTime()))
                .le(query.getEndTime() != null, ActionEvent::getOccurredAt, toLocalDateTime(query.getEndTime()))
                .orderByDesc(ActionEvent::getOccurredAt));
        for (ActionEvent event : events) {
            ActionExecution execution = byId.get(event.getActionId());
            if (execution != null) {
                entries.add(toActionEventEntry(event, execution));
            }
        }
        return entries;
    }

    private BusinessTimelineEntry toEntry(DocumentTimelineEvent event) {
        BusinessTimelineEntry entry = new BusinessTimelineEntry();
        entry.setEventId(event.getId());
        entry.setEventSource(event.getEventSource());
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

    private BusinessTimelineEntry toExecutionEntry(ActionExecution execution) {
        BusinessTimelineEntry entry = baseActionEntry(execution);
        entry.setEventId(execution.getId());
        entry.setEventSource("GLOBAL_ACTION");
        entry.setEventType("ACTION_EXECUTION");
        entry.setDisplayName(execution.getActionType());
        entry.setOccurredAt(toInstant(firstNonNull(execution.getUpdatedAt(), execution.getCreatedAt())));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", execution.getStatus());
        summary.put("payloadSummary", safeJsonOrText(execution.getPayloadSummary()));
        summary.put("resultSummary", safeJsonOrText(execution.getResultSummary()));
        summary.put("errorSummary", safeJsonOrText(execution.getErrorSummary()));
        RedactionResult redacted = redact(summary);
        entry.setSummary(redacted.summary());
        entry.setRedacted(redacted.redacted());
        return entry;
    }

    private BusinessTimelineEntry toActionEventEntry(ActionEvent event, ActionExecution execution) {
        BusinessTimelineEntry entry = baseActionEntry(execution);
        entry.setEventId(event.getId());
        entry.setEventSource("ACTION_EVENT");
        entry.setSequenceNo(event.getSequenceNo());
        entry.setEventType(event.getEventType());
        entry.setDisplayName(firstNonBlank(event.getMessage(), event.getEventType()));
        entry.setActionStatus(event.getStatus());
        entry.setTraceId(firstNonBlank(event.getTraceId(), execution.getTraceId()));
        entry.setOccurredAt(toInstant(event.getOccurredAt()));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("message", event.getMessage());
        summary.put("sequenceNo", event.getSequenceNo());
        summary.put("eventData", safeJsonOrText(event.getEventDataJson()));
        RedactionResult redacted = redact(summary);
        entry.setSummary(redacted.summary());
        entry.setRedacted(redacted.redacted());
        return entry;
    }

    private BusinessTimelineEntry baseActionEntry(ActionExecution execution) {
        BusinessTimelineEntry entry = new BusinessTimelineEntry();
        entry.setTenantId(execution.getTenantId());
        entry.setTargetType(execution.getTargetType());
        entry.setTargetId(execution.getTargetId());
        entry.setActorId(execution.getActorId());
        entry.setActorName(execution.getActorName());
        entry.setActionId(execution.getId());
        entry.setActionType(execution.getActionType());
        entry.setActionStatus(execution.getStatus());
        entry.setOwnerService(firstNonBlank(execution.getOwnerService(), execution.getTargetOwnerService()));
        entry.setOwnerExecutionRef(execution.getOwnerExecutionRef());
        entry.setTraceId(execution.getTraceId());
        entry.setCorrelationId(execution.getCorrelationId());
        return entry;
    }

    private void validateRecord(BusinessTimelineEventRecord request) {
        if (request == null
                || !hasText(request.getTenantId())
                || !hasText(request.getTargetType())
                || !hasText(request.getTargetId())
                || !hasText(request.getEventType())) {
            throw new ActionRuntimeException(
                    40060,
                    ActionErrorCategory.VALIDATION,
                    "DOCUMENT_TIMELINE_EVENT_REQUIRED");
        }
    }

    private void validateQuery(BusinessTimelineQuery query) {
        if (!hasText(query.getTenantId())) {
            throw new ActionRuntimeException(
                    40360,
                    ActionErrorCategory.SECURITY,
                    "DOCUMENT_TIMELINE_TENANT_REQUIRED");
        }
        if (!hasText(query.getTargetType())
                && !hasText(query.getTargetId())
                && !hasText(query.getActionId())
                && !hasText(query.getTraceId())
                && !hasText(query.getCorrelationId())
                && !hasText(query.getOwnerExecutionRef())) {
            throw new ActionRuntimeException(
                    40061,
                    ActionErrorCategory.VALIDATION,
                    "DOCUMENT_TIMELINE_FILTER_REQUIRED");
        }
    }

    private boolean includeSource(BusinessTimelineQuery query, String source) {
        return !hasText(query.getEventSource()) || source.equalsIgnoreCase(query.getEventSource());
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
        return SENSITIVE_KEY_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    private Map<String, Object> safeJsonOrText(String value) {
        if (!hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of("text", value);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value != null ? value : Map.of());
        } catch (Exception exception) {
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
        } catch (Exception ignored) {
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
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : LocalDateTime.now(ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime value) {
        return value != null ? value.toInstant(ZoneOffset.UTC) : null;
    }

    private LocalDateTime firstNonNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record RedactionResult(Map<String, Object> summary, boolean redacted) {
    }
}
