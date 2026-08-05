package com.triobase.common.dto.notification;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Owner 服务提交给通知运行时的 1.x 版公共契约。
 *
 * <p>{@code idempotencyKey} 在同一租户和生产者内稳定标识一次业务事实。重复事件、Kafka
 * 重放和 Temporal 重试必须返回原任务，而不是重复生成收件投影。变量只能使用模板声明的
 * 标量或 JSON 兼容结构，敏感业务详情应保留在 Owner 服务并通过 {@code resourceReference}
 * 重新鉴权读取。</p>
 */
@Data
public class NotificationRequest {
    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    private String schemaVersion = CURRENT_SCHEMA_VERSION;
    private String tenantId;
    private String producer;
    private String eventId;
    private String idempotencyKey;
    private String templateKey;
    private String templateVersion;
    private Set<String> declaredVariables = Set.of();
    private Map<String, Object> variables = new LinkedHashMap<>();
    private AudienceSelector audience;
    private ChannelIntent channelIntent;
    private Priority priority = Priority.NORMAL;
    private BusinessResourceReference resourceReference;
    private String traceId;
    private Instant expiresAt;

    public enum Priority {
        NORMAL,
        IMPORTANT,
        URGENT
    }
}
