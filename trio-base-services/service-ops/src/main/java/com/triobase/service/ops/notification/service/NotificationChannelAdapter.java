package com.triobase.service.ops.notification.service;

import com.triobase.service.ops.notification.entity.NotificationProviderEntity;

/**
 * 渠道适配器扩展点；验证过程可以访问密钥系统，但返回值只能包含脱敏诊断。
 * 实际投递能力与目录展示分离，未注册适配器的渠道不得被推断为可用。
 */
public interface NotificationChannelAdapter {
    String channelCode();

    String adapterKey();

    String version();

    ValidationResult validate(NotificationProviderEntity provider);

    record ValidationResult(boolean valid, boolean degraded, String safeSummary) {
        public static ValidationResult ready(String summary) {
            return new ValidationResult(true, false, summary);
        }

        public static ValidationResult invalid(String summary) {
            return new ValidationResult(false, false, summary);
        }

        public static ValidationResult degraded(String summary) {
            return new ValidationResult(true, true, summary);
        }
    }
}

