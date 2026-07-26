package com.triobase.service.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.workflow.entity.WorkflowActionAuditEvent;
import com.triobase.service.workflow.mapper.WorkflowActionAuditEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowActionAuditQueryService {

    private static final int DEFAULT_LIMIT = 200;

    private final WorkflowActionAuditEventMapper mapper;

    public List<WorkflowActionAuditEvent> byActionId(String tenantId, String actionId) {
        return select(base(tenantId)
                .eq(WorkflowActionAuditEvent::getActionId, actionId)
                .orderByDesc(WorkflowActionAuditEvent::getOccurredAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    public List<WorkflowActionAuditEvent> byTarget(String tenantId, String targetType, String targetId) {
        return select(base(tenantId)
                .eq(WorkflowActionAuditEvent::getTargetType, targetType)
                .eq(WorkflowActionAuditEvent::getTargetId, targetId)
                .orderByDesc(WorkflowActionAuditEvent::getOccurredAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    public List<WorkflowActionAuditEvent> byTraceId(String tenantId, String traceId) {
        return select(base(tenantId)
                .eq(WorkflowActionAuditEvent::getTraceId, traceId)
                .orderByDesc(WorkflowActionAuditEvent::getOccurredAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    public List<WorkflowActionAuditEvent> byCorrelationId(String tenantId, String correlationId) {
        return select(base(tenantId)
                .eq(WorkflowActionAuditEvent::getCorrelationId, correlationId)
                .orderByDesc(WorkflowActionAuditEvent::getOccurredAt)
                .last("LIMIT " + DEFAULT_LIMIT));
    }

    private LambdaQueryWrapper<WorkflowActionAuditEvent> base(String tenantId) {
        return new LambdaQueryWrapper<WorkflowActionAuditEvent>()
                .eq(StringUtils.hasText(tenantId), WorkflowActionAuditEvent::getTenantId, tenantId);
    }

    private List<WorkflowActionAuditEvent> select(LambdaQueryWrapper<WorkflowActionAuditEvent> query) {
        return mapper.selectList(query);
    }
}
