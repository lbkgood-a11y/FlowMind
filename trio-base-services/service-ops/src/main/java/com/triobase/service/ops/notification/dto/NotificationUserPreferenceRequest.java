package com.triobase.service.ops.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NotificationUserPreferenceRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_.-]{1,64}") String categoryCode,
        @NotBlank @Pattern(regexp = "IN_APP|EMAIL|SMS|WE_COM|DINGTALK") String channelCode,
        boolean enabled,
        @Valid NotificationRoutingPolicyRequest.QuietHours quietHours) {
}

