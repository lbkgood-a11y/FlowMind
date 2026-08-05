package com.triobase.service.ops.notification.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.ops.notification.config.NotificationCutoverProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 解析当前租户的通知中心切换模式。
 *
 * <p>全局开关与租户名单采用 AND 语义，配置缺失或空租户一律回退到 legacy。读开关独立于双写开关，
 * 允许事故时先切回 legacy 读而继续保留双写观测；任何回滚都不会修改 v2 数据。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationCutoverService {

    private final NotificationCutoverProperties properties;

    public boolean dualWriteEnabled(String tenantId) {
        return validTenant(tenantId) && properties.isDualWriteEnabled()
                && properties.getDualWriteTenants().contains(tenantId);
    }

    public boolean readV2Enabled(String tenantId) {
        return validTenant(tenantId) && properties.isReadV2Enabled()
                && properties.getReadV2Tenants().contains(tenantId);
    }

    public boolean legacyWriteEnabled(String tenantId) {
        return validTenant(tenantId) && properties.isLegacyWriteEnabled()
                && !properties.getLegacyWriteDisabledTenants().contains(tenantId);
    }

    public void requireLegacyWrite(String tenantId) {
        if (!legacyWriteEnabled(tenantId)) {
            throw new BizException(45531, "NOTIFICATION_LEGACY_WRITE_DISABLED");
        }
    }

    public void requireV2Read(String tenantId) {
        if (!readV2Enabled(tenantId)) {
            throw new BizException(45530, "NOTIFICATION_V2_READ_NOT_ENABLED");
        }
    }

    private boolean validTenant(String tenantId) {
        return tenantId != null && !tenantId.isBlank();
    }
}
