package com.triobase.service.ops.notification.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.ops.notification.entity.NotificationConfigurationAuditEntity;
import com.triobase.service.ops.notification.mapper.NotificationConfigurationAuditMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 写入渠道配置的追加式安全审计。
 *
 * <p>调用方只能传枚举式摘要，禁止传请求 JSON、模板内容或凭据引用；违反安全字符集时
 * 整个配置事务失败，避免“配置成功但审计缺失”的不完整状态。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationConfigurationAuditService {
    private final NotificationConfigurationAuditMapper mapper;
    private final RequestContextService contextService;

    public void record(String resourceType, String resourceKey, String actionCode, String safeDetails) {
        if (!safe(resourceType, 32) || !safe(resourceKey, 256) || !safe(actionCode, 48)
                || (safeDetails != null && !safe(safeDetails, 512))) {
            throw new BizException(45530, "CONFIG_AUDIT_DETAILS_UNSAFE");
        }
        NotificationConfigurationAuditEntity audit = new NotificationConfigurationAuditEntity();
        audit.setId(UUID.randomUUID().toString().replace("-", ""));
        audit.setTenantId(contextService.tenantId());
        audit.setActorUserId(contextService.userId());
        audit.setResourceType(resourceType);
        audit.setResourceKey(resourceKey);
        audit.setActionCode(actionCode);
        audit.setSafeDetails(safeDetails);
        audit.setTraceId(TraceUtil.getTraceId());
        mapper.insert(audit);
    }

    private boolean safe(String value, int max) {
        return value != null && value.length() <= max && value.matches("[A-Za-z0-9_.:-]+");
    }
}
