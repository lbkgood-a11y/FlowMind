package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.auth.entity.SysRoleAuthAudit;
import com.triobase.service.auth.entity.SysUserRole;
import com.triobase.service.auth.mapper.RoleAuthAuditMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleAuthorizationAuditService {

    private final RoleAuthAuditMapper auditMapper;
    private final UserRoleMapper userRoleMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(String tenantId, String roleId, String draftId, String releaseId,
                       String eventType, String businessSummary, Object technicalEvidence) {
        SysRoleAuthAudit audit = new SysRoleAuthAudit();
        audit.setTenantId(tenantId);
        audit.setRoleId(roleId);
        audit.setDraftId(draftId);
        audit.setReleaseId(releaseId);
        audit.setEventType(eventType);
        audit.setActorId(currentActor());
        audit.setBusinessSummary(StringUtils.hasText(businessSummary) ? businessSummary : "未提供变更摘要");
        audit.setTechnicalEvidence(redactedJson(technicalEvidence));
        audit.setAffectedUserCount(affectedUsers(roleId));
        audit.setTraceId(TraceUtil.getTraceId());
        audit.setOccurredAt(LocalDateTime.now());
        auditMapper.insert(audit);
    }

    public long affectedUsers(String roleId) {
        if (!StringUtils.hasText(roleId)) {
            return 0L;
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        return count == null ? 0L : count;
    }

    private String redactedJson(Object value) {
        if (value == null) {
            return null;
        }
        JsonNode node = objectMapper.valueToTree(value);
        redact(node);
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            return "{\"evidence\":\"unavailable\"}";
        }
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey().toLowerCase();
                if (key.contains("token") || key.contains("password") || key.contains("secret")) {
                    objectNode.put(field.getKey(), "***");
                } else {
                    redact(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::redact);
        }
    }

    private String currentActor() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }
}
