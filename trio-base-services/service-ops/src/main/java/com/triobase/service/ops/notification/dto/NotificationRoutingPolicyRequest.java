package com.triobase.service.ops.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NotificationRoutingPolicyRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_.-]{1,64}") String categoryCode,
        @NotBlank @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priorityCode,
        @NotEmpty @Size(max = 5) List<@Pattern(regexp = "IN_APP|EMAIL|SMS|WE_COM|DINGTALK") String> orderedChannels,
        boolean fallbackEnabled,
        @Valid QuietHours quietHours,
        boolean mandatoryCategory) {
    public record QuietHours(@Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String start,
                             @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String end,
                             @NotBlank @Size(max = 64) String zoneId) { }
}

