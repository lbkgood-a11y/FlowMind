package com.triobase.service.ops.notification.service;

import com.triobase.service.ops.notification.mapper.NotificationRetentionMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 保留任务调度入口；默认关闭，启用前必须完成租户策略、冻结流程和备份恢复审批。
 * 单次最多载入 1000 个租户策略，每个租户由独立事务处理。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "triobase.notification.retention", name = "enabled", havingValue = "true")
public class NotificationRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);

    private final NotificationRetentionMapper mapper;
    private final NotificationRetentionService service;
    private final NotificationRuntimeMetrics metrics;

    @Scheduled(cron = "${triobase.notification.retention.cron:0 30 2 * * *}")
    public void purgeExpiredEvidence() {
        LocalDateTime now = LocalDateTime.now();
        mapper.findEnabledPolicies(1_000).forEach(policy -> {
            try {
                service.purgeTenant(policy, now);
            } catch (RuntimeException failure) {
                // 单租户清理失败必须隔离；日志不记录租户、策略或异常正文，详细定位依赖 Trace/平台错误链路。
                metrics.retentionFailed();
                log.error("Notification retention tenant batch failed errorCategory=RETENTION_BATCH_FAILED");
            }
        });
    }
}
