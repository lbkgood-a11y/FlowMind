package com.triobase.service.ops.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** 管理端只提交密钥系统引用，不接受任何凭据值。 */
public record NotificationProviderRequest(
        @NotBlank @Pattern(regexp = "EMAIL|SMS|WE_COM|DINGTALK") String channelCode,
        @NotBlank @Pattern(regexp = "[A-Z0-9_.-]{1,64}") String providerKey,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 256) String credentialReference,
        Map<String, String> settings) {
}

