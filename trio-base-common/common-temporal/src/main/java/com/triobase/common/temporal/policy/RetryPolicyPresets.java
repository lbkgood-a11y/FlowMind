package com.triobase.common.temporal.policy;

import io.temporal.common.RetryOptions;

import java.time.Duration;

/**
 * Temporal Activity RetryPolicy 预设 — 铁律 7 & CI 门禁要求 Activity 必须包含 RetryPolicy。
 */
public final class RetryPolicyPresets {

    private RetryPolicyPresets() {
    }

    /** 标准重试：初始 1s，最大 60s，最多 3 次 */
    public static RetryOptions standard() {
        return RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofSeconds(1))
                .setMaximumInterval(Duration.ofSeconds(60))
                .setMaximumAttempts(3)
                .setBackoffCoefficient(2.0)
                .build();
    }

    /** 幂等操作重试：适合扣款/发信等关键 Activity */
    public static RetryOptions idempotent() {
        return RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofMillis(500))
                .setMaximumInterval(Duration.ofSeconds(30))
                .setMaximumAttempts(5)
                .setBackoffCoefficient(2.0)
                .setDoNotRetry(IllegalArgumentException.class.getName())
                .build();
    }
}
