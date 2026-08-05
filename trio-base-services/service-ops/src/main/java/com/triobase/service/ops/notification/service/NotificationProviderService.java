package com.triobase.service.ops.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.ops.notification.dto.NotificationProviderRequest;
import com.triobase.service.ops.notification.dto.NotificationProviderView;
import com.triobase.service.ops.notification.entity.NotificationProviderEntity;
import com.triobase.service.ops.notification.mapper.NotificationProviderMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 管理租户级渠道提供商及外部密钥引用。
 *
 * <p>接口从不接收或返回 secret 值。settings 仅允许非敏感连接参数，疑似凭据键默认拒绝；
 * 新增或换绑后强制 disabled，必须由后续适配器验证流程显式激活。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationProviderService {

    private static final Pattern REFERENCE =
            Pattern.compile("^(vault|nacos|kms)://[A-Za-z0-9_./:-]{1,240}$");
    private static final Set<String> SENSITIVE_FRAGMENTS =
            Set.of("secret", "password", "passwd", "token", "apikey", "api_key", "credential", "privatekey");

    private final NotificationProviderMapper mapper;
    private final RequestContextService contextService;
    private final ObjectMapper objectMapper;
    private final NotificationConfigurationAuditService auditService;

    public List<NotificationProviderView> listCurrentTenant() {
        return mapper.findByTenant(contextService.tenantId()).stream().map(this::view).toList();
    }

    @Transactional
    public NotificationProviderView save(NotificationProviderRequest request) {
        validateReference(request.credentialReference());
        Map<String, String> settings = sanitizeSettings(request.settings());
        String tenantId = contextService.tenantId();
        String settingsJson = write(settings);
        NotificationProviderEntity existing = mapper.findOwned(
                tenantId, request.channelCode(), request.providerKey());
        if (existing == null) {
            existing = new NotificationProviderEntity();
            existing.setId(UUID.randomUUID().toString().replace("-", ""));
            existing.setTenantId(tenantId);
            existing.setChannelCode(request.channelCode());
            existing.setProviderKey(request.providerKey());
            existing.setDisplayName(request.displayName());
            existing.setCredentialReference(request.credentialReference());
            existing.setSettingsJson(settingsJson);
            existing.setEnabled(0);
            mapper.insert(existing);
        } else {
            mapper.updateOwned(tenantId, request.channelCode(), request.providerKey(),
                    request.displayName(), request.credentialReference(), settingsJson);
            existing = mapper.findOwned(tenantId, request.channelCode(), request.providerKey());
        }
        auditService.record("PROVIDER", request.channelCode() + ":" + request.providerKey(),
                "CREDENTIAL_REFERENCE_BOUND", "REFERENCE_MASKED");
        return view(existing);
    }

    private void validateReference(String reference) {
        if (!REFERENCE.matcher(reference).matches()) {
            throw new BizException(45501, "CREDENTIAL_REFERENCE_INVALID");
        }
    }

    private Map<String, String> sanitizeSettings(Map<String, String> input) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (input == null) return sanitized;
        input.forEach((key, value) -> {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("-", "");
            if (SENSITIVE_FRAGMENTS.stream().anyMatch(normalized::contains)) {
                throw new BizException(45502, "PROVIDER_SETTING_SECRET_FORBIDDEN");
            }
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
                    || value == null || value.length() > 512) {
                throw new BizException(45503, "PROVIDER_SETTING_INVALID");
            }
            sanitized.put(key, value);
        });
        return sanitized;
    }

    private NotificationProviderView view(NotificationProviderEntity entity) {
        String reference = entity.getCredentialReference();
        String masked = reference == null ? null : reference.substring(0, reference.indexOf("://") + 3) + "***";
        return new NotificationProviderView(entity.getChannelCode(), entity.getProviderKey(),
                entity.getDisplayName(), reference != null, masked, read(entity.getSettingsJson()),
                Integer.valueOf(1).equals(entity.getEnabled()));
    }

    private String write(Map<String, String> settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException error) {
            throw new BizException(45503, "PROVIDER_SETTING_INVALID");
        }
    }

    private Map<String, String> read(String settingsJson) {
        if (settingsJson == null || settingsJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(settingsJson, new TypeReference<>() { });
        } catch (JsonProcessingException error) {
            return Map.of();
        }
    }
}
