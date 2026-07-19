package com.triobase.service.action.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionEventType;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionEventPayload;
import com.triobase.service.action.entity.ActionEvent;
import com.triobase.service.action.entity.ActionExecution;
import com.triobase.service.action.mapper.ActionEventMapper;
import com.triobase.service.action.mapper.ActionExecutionMapper;
import com.triobase.service.action.support.ActionJsonSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ActionEventRepository {

    private static final String EVENT_PREFIX = "evt_";

    private final ActionEventMapper actionEventMapper;
    private final ActionExecutionMapper actionExecutionMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public ActionEvent append(ActionExecution execution,
                              ActionEventType eventType,
                              ActionStatus status,
                              String message,
                              Map<String, Object> data) {
        ActionEvent event = new ActionEvent();
        event.setId(newEventId());
        event.setActionId(execution.getId());
        event.setTenantId(execution.getTenantId());
        event.setEventType(eventType.name());
        event.setStatus(status != null ? status.name() : null);
        event.setSequenceNo(nextSequence(execution.getId()));
        event.setMessage(message);
        event.setEventDataJson(ActionJsonSupport.boundedJson(objectMapper, data));
        event.setTraceId(execution.getTraceId());
        event.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
        actionEventMapper.insert(event);
        return event;
    }

    @Transactional
    public ActionEvent append(ActionEventPayload payload) {
        ActionExecution execution = Optional.ofNullable(actionExecutionMapper.selectById(payload.getActionId()))
                .orElseThrow(() -> new IllegalArgumentException("action execution not found: " + payload.getActionId()));
        ActionEvent event = new ActionEvent();
        event.setId(firstNonBlank(payload.getEventId(), newEventId()));
        event.setActionId(execution.getId());
        event.setTenantId(execution.getTenantId());
        event.setEventType(payload.getEventType().name());
        event.setStatus(payload.getStatus() != null ? payload.getStatus().name() : null);
        event.setSequenceNo(nextSequence(execution.getId()));
        event.setMessage(payload.getMessage());
        event.setEventDataJson(ActionJsonSupport.boundedJson(objectMapper, payload.getData()));
        event.setTraceId(execution.getTraceId());
        event.setOccurredAt(toLocalDateTime(payload.getOccurredAt()));
        actionEventMapper.insert(event);
        return event;
    }

    public List<ActionEvent> findByActionId(String actionId) {
        return actionEventMapper.selectList(new LambdaQueryWrapper<ActionEvent>()
                .eq(ActionEvent::getActionId, actionId)
                .orderByAsc(ActionEvent::getSequenceNo));
    }

    private Integer nextSequence(String actionId) {
        List<ActionEvent> latest = actionEventMapper.selectList(new LambdaQueryWrapper<ActionEvent>()
                .eq(ActionEvent::getActionId, actionId)
                .orderByDesc(ActionEvent::getSequenceNo)
                .last("LIMIT 1"));
        if (latest.isEmpty() || latest.get(0).getSequenceNo() == null) {
            return 1;
        }
        return latest.get(0).getSequenceNo() + 1;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        Instant actual = instant != null ? instant : Instant.now();
        return LocalDateTime.ofInstant(actual, ZoneOffset.UTC);
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first.trim() : fallback;
    }

    private String newEventId() {
        return EVENT_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
