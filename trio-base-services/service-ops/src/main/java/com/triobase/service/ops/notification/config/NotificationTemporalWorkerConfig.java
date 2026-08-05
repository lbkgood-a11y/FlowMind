package com.triobase.service.ops.notification.config;

import com.triobase.common.temporal.interceptor.TraceContextPropagator;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.spring.boot.TemporalOptionsCustomizer;
import io.temporal.spring.boot.WorkerOptionsCustomizer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 通知 Worker 的宿主配置。
 *
 * <p>任务队列必须与应用名一致，使 Worker 与 service-ops 生命周期及部署边界绑定；若配置漂移，
 * 启动时直接拒绝，避免通知任务被无业务上下文的通用 Worker 消费。</p>
 */
@Configuration
public class NotificationTemporalWorkerConfig {

    private final String applicationName;
    private final String taskQueue;
    private final int maxConcurrentActivities;
    private final int maxConcurrentWorkflowTasks;

    public NotificationTemporalWorkerConfig(
            @Value("${spring.application.name}") String applicationName,
            @Value("${triobase.notification.temporal.task-queue:${spring.application.name}}") String taskQueue,
            @Value("${triobase.notification.temporal.max-concurrent-activities:100}") int maxConcurrentActivities,
            @Value("${triobase.notification.temporal.max-concurrent-workflow-tasks:50}")
            int maxConcurrentWorkflowTasks) {
        this.applicationName = applicationName;
        this.taskQueue = taskQueue;
        this.maxConcurrentActivities = maxConcurrentActivities;
        this.maxConcurrentWorkflowTasks = maxConcurrentWorkflowTasks;
    }

    @PostConstruct
    void validateTaskQueueBinding() {
        if (!applicationName.equals(taskQueue)) {
            throw new IllegalStateException("NOTIFICATION_TEMPORAL_TASK_QUEUE_MUST_MATCH_APPLICATION_NAME");
        }
    }

    @Bean
    TemporalOptionsCustomizer<WorkflowClientOptions.Builder> notificationWorkflowClientCustomizer() {
        return builder -> builder.setContextPropagators(List.of(new TraceContextPropagator()));
    }

    @Bean
    WorkerOptionsCustomizer notificationWorkerOptionsCustomizer() {
        return (builder, workerName, configuredTaskQueue) -> {
            if (taskQueue.equals(configuredTaskQueue)) {
                builder.setMaxConcurrentActivityExecutionSize(maxConcurrentActivities);
                builder.setMaxConcurrentWorkflowTaskExecutionSize(maxConcurrentWorkflowTasks);
            }
            return builder;
        };
    }
}
