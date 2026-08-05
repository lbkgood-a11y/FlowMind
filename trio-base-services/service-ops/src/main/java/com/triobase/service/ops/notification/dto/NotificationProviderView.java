package com.triobase.service.ops.notification.dto;

import java.util.Map;

/** 对外投影隐藏完整凭据引用，且模型中不存在凭据值字段。 */
public record NotificationProviderView(String channelCode, String providerKey, String displayName,
                                       boolean credentialConfigured, String maskedCredentialReference,
                                       Map<String, String> settings, boolean enabled) {
}

