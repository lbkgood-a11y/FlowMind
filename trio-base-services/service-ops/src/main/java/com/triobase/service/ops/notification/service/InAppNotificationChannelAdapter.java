package com.triobase.service.ops.notification.service;

import com.triobase.service.ops.notification.entity.NotificationProviderEntity;
import org.springframework.stereotype.Component;

/** 站内投影由 service-ops 自有运行时提供，不依赖外部凭据或网络连通性。 */
@Component
public class InAppNotificationChannelAdapter implements NotificationChannelAdapter {
    @Override
    public String channelCode() {
        return "IN_APP";
    }

    @Override
    public String adapterKey() {
        return "service-ops-in-app";
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public ValidationResult validate(NotificationProviderEntity provider) {
        return ValidationResult.ready("IN_APP_RUNTIME_READY");
    }
}

