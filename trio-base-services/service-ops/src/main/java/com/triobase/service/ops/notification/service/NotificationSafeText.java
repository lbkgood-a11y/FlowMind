package com.triobase.service.ops.notification.service;

import java.util.regex.Pattern;

/** 通知诊断文本的末端防泄漏工具；用于持久化、日志或返回前的纵深防御，不替代入口数据最小化。 */
final class NotificationSafeText {

    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+\\-/=]+");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(password|passwd|secret|token|credential|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<!\\d)\\d{15,19}(?!\\d)");
    private static final Pattern CLASSIFICATION = Pattern.compile("[A-Z0-9_]{1,64}");

    private NotificationSafeText() {
    }

    static String summary(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String safe = BEARER.matcher(value).replaceAll("Bearer ***REDACTED***");
        safe = SECRET_ASSIGNMENT.matcher(safe).replaceAll("$1=***REDACTED***");
        safe = PHONE.matcher(safe).replaceAll("***REDACTED***");
        safe = LONG_NUMBER.matcher(safe).replaceAll("***REDACTED***");
        return safe.length() <= 512 ? safe : safe.substring(0, 512);
    }

    static String classification(String value) {
        return value != null && CLASSIFICATION.matcher(value).matches()
                ? value : "DELIVERY_ACTIVITY_FAILED";
    }
}

