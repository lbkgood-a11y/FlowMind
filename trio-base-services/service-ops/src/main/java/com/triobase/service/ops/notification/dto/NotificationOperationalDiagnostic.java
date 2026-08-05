package com.triobase.service.ops.notification.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 脱敏运营诊断，不包含请求正文、收件人、模板变量或凭据。 */
public record NotificationOperationalDiagnostic(
        String taskId,
        String state,
        String correlationId,
        String traceId,
        List<Attempt> attempts) {

    public record Attempt(String stage, boolean retryable, String errorCategory,
                          String sanitizedMessage, LocalDateTime occurredAt) {
    }
}
