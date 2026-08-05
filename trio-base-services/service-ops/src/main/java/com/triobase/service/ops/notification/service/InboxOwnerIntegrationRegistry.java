package com.triobase.service.ops.notification.service;

import com.triobase.common.core.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 未注册 Owner 默认拒绝，避免资源引用退化为通用 HTTP 客户端。 */
@Component
public class InboxOwnerIntegrationRegistry {

    private final Map<String, InboxOwnerIntegration> integrations;

    public InboxOwnerIntegrationRegistry(Collection<InboxOwnerIntegration> integrations) {
        this.integrations = integrations.stream().collect(Collectors.toUnmodifiableMap(
                InboxOwnerIntegration::ownerService, Function.identity()));
    }

    public InboxOwnerIntegration require(String ownerService) {
        InboxOwnerIntegration integration = integrations.get(ownerService);
        if (integration == null) {
            throw new BizException(45510, "INBOX_OWNER_INTEGRATION_NOT_REGISTERED");
        }
        return integration;
    }
}
