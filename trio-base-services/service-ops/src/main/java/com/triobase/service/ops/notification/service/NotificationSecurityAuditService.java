package com.triobase.service.ops.notification.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.ops.notification.entity.NotificationSecurityAuditEntity;
import com.triobase.service.ops.notification.mapper.NotificationSecurityAuditMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 写入高敏通知操作审计。
 *
 * <p>字段只接受受控字符，拒绝正文、收件人列表和凭据引用；调用方应与被审计读取处于同一事务，
 * 防止返回敏感证据但遗漏访问记录。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationSecurityAuditService {

    private final NotificationSecurityAuditMapper mapper;
    private final RequestContextService contextService;

    public void record(String actionCode, String resourceType, String resourceId, String safeDetails) {
        if (!safe(actionCode, 48) || !safe(resourceType, 32) || !safe(resourceId, 64)
                || (safeDetails != null && !safe(safeDetails, 512))) {
            throw new BizException(45531, "SECURITY_AUDIT_DETAILS_UNSAFE");
        }
        NotificationSecurityAuditEntity audit = new NotificationSecurityAuditEntity();
        audit.setId(UlidGenerator.nextUlid());
        audit.setTenantId(contextService.tenantId());
        audit.setActorUserId(contextService.userId());
        audit.setActionCode(actionCode);
        audit.setResourceType(resourceType);
        audit.setResourceId(resourceId);
        audit.setSafeDetails(safeDetails);
        audit.setTraceId(TraceUtil.getTraceId());
        audit.setOccurredAt(LocalDateTime.now());
        mapper.insert(audit);
    }

    private boolean safe(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max
                && value.matches("[A-Za-z0-9_.:=-]+");
    }
}

