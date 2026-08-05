package com.triobase.service.ops.notification.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationTemplateView(String templateId, String versionId, int versionNo,
                                       String templateKey, String channelCode, String localeCode,
                                       String state, String subjectTemplate, String bodyTemplate,
                                       Map<String, String> variableSchema,
                                       LocalDateTime effectiveFrom, LocalDateTime effectiveUntil) {
}

