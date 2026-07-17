package com.triobase.service.openapi.config;

import com.triobase.common.temporal.interceptor.TraceContextPropagator;
import com.triobase.service.openapi.temporal.OpenApiContextPropagator;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.spring.boot.TemporalOptionsCustomizer;
import io.temporal.spring.boot.WorkerOptionsCustomizer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiTemporalWorkerConfig {

    private final String applicationName;
    private final String taskQueue;
    private final int maxConcurrentActivities;
    private final int maxConcurrentWorkflowTasks;

    public OpenApiTemporalWorkerConfig(
            @Value("${spring.application.name}") String applicationName,
            @Value("${triobase.openapi.temporal.task-queue:${spring.application.name}}") String taskQueue,
            @Value("${triobase.openapi.temporal.max-concurrent-activities:100}") int maxConcurrentActivities,
            @Value("${triobase.openapi.temporal.max-concurrent-workflow-tasks:50}")
            int maxConcurrentWorkflowTasks) {
        this.applicationName = applicationName;
        this.taskQueue = taskQueue;
        this.maxConcurrentActivities = maxConcurrentActivities;
        this.maxConcurrentWorkflowTasks = maxConcurrentWorkflowTasks;
    }

    @PostConstruct
    void validateTaskQueueBinding() {
        if (!applicationName.equals(taskQueue)) {
            throw new IllegalStateException(
                    "OPENAPI_TEMPORAL_TASK_QUEUE_MUST_MATCH_APPLICATION_NAME");
        }
    }

    @Bean
    TemporalOptionsCustomizer<WorkflowClientOptions.Builder> openApiWorkflowClientCustomizer() {
        return builder -> builder.setContextPropagators(List.of(
                new TraceContextPropagator(), new OpenApiContextPropagator()));
    }

    @Bean
    WorkerOptionsCustomizer openApiWorkerOptionsCustomizer() {
        return (builder, workerName, configuredTaskQueue) -> {
            if (taskQueue.equals(configuredTaskQueue)) {
                builder.setMaxConcurrentActivityExecutionSize(maxConcurrentActivities);
                builder.setMaxConcurrentWorkflowTaskExecutionSize(maxConcurrentWorkflowTasks);
            }
            return builder;
        };
    }
}
