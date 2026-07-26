package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.lowcode.entity.LowcodeActionAuditEvent;
import com.triobase.service.lowcode.mapper.LowcodeActionAuditEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LowcodeActionAuditQueryService {

    private static final int DEFAULT_LIMIT = 200;

    private final LowcodeActionAuditEventMapper mapper;

    public List<LowcodeActionAuditEvent> byActionId(String tenantId, String actionId) {
        return select(base(tenantId)
                .eq(LowcodeActionAuditEvent::getActionId, actionId)
                .orderByDesc(LowcodeActionAuditEvent::getOccurredAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    public List<LowcodeActionAuditEvent> byTarget(String tenantId, String targetType, String targetId) {
        return select(base(tenantId)
                .eq(LowcodeActionAuditEvent::getTargetType, targetType)
                .eq(LowcodeActionAuditEvent::getTargetId, targetId)
                .orderByDesc(LowcodeActionAuditEvent::getOccurredAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    public List<LowcodeActionAuditEvent> byTraceId(String tenantId, String traceId) {
        return select(base(tenantId)
                .eq(LowcodeActionAuditEvent::getTraceId, traceId)
                .orderByDesc(LowcodeActionAuditEvent::getOccurredAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    public List<LowcodeActionAuditEvent> byCorrelationId(String tenantId, String correlationId) {
        return select(base(tenantId)
                .eq(LowcodeActionAuditEvent::getCorrelationId, correlationId)
                .orderByDesc(LowcodeActionAuditEvent::getOccurredAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    private LambdaQueryWrapper<LowcodeActionAuditEvent> base(String tenantId) {
        return new LambdaQueryWrapper<LowcodeActionAuditEvent>()
                .eq(StringUtils.hasText(tenantId), LowcodeActionAuditEvent::getTenantId, tenantId);
    }

    private List<LowcodeActionAuditEvent> select(LambdaQueryWrapper<LowcodeActionAuditEvent> query) {
        return mapper.selectList(query);
    }
}
