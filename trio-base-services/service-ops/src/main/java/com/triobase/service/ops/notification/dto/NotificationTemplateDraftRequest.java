package com.triobase.service.ops.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Map;

/** 模板草稿契约；变量 schema 的值限定为 STRING、NUMBER 或 BOOLEAN。 */
public record NotificationTemplateDraftRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_.-]{1,128}") String templateKey,
        @NotBlank @Pattern(regexp = "IN_APP|EMAIL|SMS|WE_COM|DINGTALK") String channelCode,
        @NotBlank @Pattern(regexp = "[a-z]{2}(?:-[A-Z]{2})?") String localeCode,
        @Size(max = 256) String subjectTemplate,
        @NotBlank @Size(max = 10000) String bodyTemplate,
        Map<String, String> variableSchema,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveUntil) {
}

