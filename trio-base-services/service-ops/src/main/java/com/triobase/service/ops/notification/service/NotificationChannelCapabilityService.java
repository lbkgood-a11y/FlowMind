package com.triobase.service.ops.notification.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.ops.notification.dto.ChannelValidationView;
import com.triobase.service.ops.notification.entity.NotificationChannelEntity;
import com.triobase.service.ops.notification.entity.NotificationProviderEntity;
import com.triobase.service.ops.notification.mapper.NotificationChannelMapper;
import com.triobase.service.ops.notification.mapper.NotificationProviderMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 执行能力验证并实施“只有 READY 才能激活”的硬门禁。 */
@Service
@RequiredArgsConstructor
public class NotificationChannelCapabilityService {

    private final NotificationChannelMapper channelMapper;
    private final NotificationProviderMapper providerMapper;
    private final NotificationChannelAdapterRegistry adapterRegistry;
    private final RequestContextService contextService;
    private final NotificationConfigurationAuditService auditService;

    @Transactional
    public ChannelValidationView validate(String channelCode, String providerKey) {
        String tenantId = contextService.tenantId();
        NotificationChannelEntity channel = requireChannel(tenantId, channelCode);
        NotificationChannelAdapter adapter = adapterRegistry.find(channelCode);
        if (adapter == null) {
            channelMapper.updateValidation(tenantId, channelCode, "NOT_CONNECTED", null, null,
                    "ADAPTER_NOT_INSTALLED");
            return new ChannelValidationView(channelCode, "NOT_CONNECTED", null, null,
                    "ADAPTER_NOT_INSTALLED");
        }

        NotificationProviderEntity provider = null;
        if (!"IN_APP".equals(channelCode)) {
            provider = providerKey == null ? null : providerMapper.findOwned(tenantId, channelCode, providerKey);
            if (provider == null || provider.getCredentialReference() == null) {
                channelMapper.updateValidation(tenantId, channelCode, "INVALID", adapter.adapterKey(),
                        adapter.version(), "PROVIDER_OR_CREDENTIAL_REFERENCE_MISSING");
                return view(channelCode, "INVALID", adapter, "PROVIDER_OR_CREDENTIAL_REFERENCE_MISSING");
            }
        }

        NotificationChannelAdapter.ValidationResult result;
        try {
            result = adapter.validate(provider);
        } catch (RuntimeException validationFailure) {
            // 外部异常不得穿透到接口或日志形成凭据/地址泄漏，统一降级为安全状态。
            result = NotificationChannelAdapter.ValidationResult.invalid("CONNECTIVITY_VALIDATION_FAILED");
        }
        String state = result.degraded() ? "DEGRADED" : result.valid() ? "READY" : "INVALID";
        channelMapper.updateValidation(tenantId, channelCode, state, adapter.adapterKey(),
                adapter.version(), result.safeSummary());
        auditService.record("CHANNEL", channelCode, "VALIDATED", state);
        return view(channelCode, state, adapter, result.safeSummary());
    }

    @Transactional
    public void setEnabled(String channelCode, boolean enabled) {
        String tenantId = contextService.tenantId();
        NotificationChannelEntity channel = requireChannel(tenantId, channelCode);
        if (enabled && !"READY".equals(channel.getCapabilityState())) {
            throw new BizException(45504, "CHANNEL_NOT_READY");
        }
        if (channelMapper.updateEnabledGuarded(tenantId, channelCode, enabled ? 1 : 0) != 1) {
            throw new BizException(45504, "CHANNEL_NOT_READY");
        }
        auditService.record("CHANNEL", channelCode, enabled ? "ENABLED" : "DISABLED", "STATE_CHANGED");
    }

    private NotificationChannelEntity requireChannel(String tenantId, String channelCode) {
        NotificationChannelEntity channel = channelMapper.findOwned(tenantId, channelCode);
        if (channel == null) throw new BizException(45505, "CHANNEL_NOT_FOUND");
        return channel;
    }

    private ChannelValidationView view(String channelCode, String state,
                                       NotificationChannelAdapter adapter, String summary) {
        return new ChannelValidationView(channelCode, state, adapter.adapterKey(), adapter.version(), summary);
    }
}
