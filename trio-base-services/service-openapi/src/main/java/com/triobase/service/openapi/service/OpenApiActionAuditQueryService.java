package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.openapi.domain.entity.AuditEvent;
import com.triobase.service.openapi.infrastructure.mapper.AuditEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenApiActionAuditQueryService {

    private static final int DEFAULT_LIMIT = 200;

    private final AuditEventMapper mapper;

    public List<AuditEvent> byActionId(String tenantId, String actionId) {
        return select(base(tenantId)
                .eq(AuditEvent::getActionId, actionId)
                .orderByDesc(AuditEvent::getCreatedAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    public List<AuditEvent> byTarget(String tenantId, String targetType, String targetId) {
        return select(base(tenantId)
                .eq(AuditEvent::getResourceType, targetType)
                .eq(AuditEvent::getResourceId, targetId)
                .orderByDesc(AuditEvent::getCreatedAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    public List<AuditEvent> byTraceId(String tenantId, String traceId) {
        return select(base(tenantId)
                .eq(AuditEvent::getTraceId, traceId)
                .orderByDesc(AuditEvent::getCreatedAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    public List<AuditEvent> byCorrelationId(String tenantId, String correlationId) {
        return select(base(tenantId)
                .eq(AuditEvent::getActionCorrelationId, correlationId)
                .orderByDesc(AuditEvent::getCreatedAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    private LambdaQueryWrapper<AuditEvent> base(String tenantId) {
        return new LambdaQueryWrapper<AuditEvent>()
                .eq(StringUtils.hasText(tenantId), AuditEvent::getTenantId, tenantId)
                .isNotNull(AuditEvent::getActionId);
    }

    private List<AuditEvent> select(LambdaQueryWrapper<AuditEvent> query) {
        return mapper.selectList(query);
    }
}
