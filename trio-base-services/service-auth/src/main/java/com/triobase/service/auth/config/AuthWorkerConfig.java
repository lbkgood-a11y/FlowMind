package com.triobase.service.auth.config;

import com.triobase.common.temporal.base.BaseActivity;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Temporal Worker 配置 — 铁律 5：Worker 内嵌在 Spring Boot 应用中，随应用一同启动。
 * Phase 1 暂无 Activity，Worker 预置就绪，后续业务接入时直接注册 Activity 即可。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "temporal.enabled", havingValue = "true")
public class AuthWorkerConfig {

    @Value("${temporal.namespace:default}")
    private String namespace;

    @Value("${temporal.task-queue:service-auth}")
    private String taskQueue;

    @Value("${temporal.host:localhost:7233}")
    private String temporalHost;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        log.info("Connecting to Temporal server at {}", temporalHost);
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(temporalHost)
                        .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(namespace)
                        .build());
    }

    @Bean(destroyMethod = "shutdown")
    public WorkerFactory workerFactory(WorkflowClient client,
                                       List<BaseActivity> activities) {
        WorkerFactory factory = WorkerFactory.newInstance(client,
                WorkerFactoryOptions.newBuilder().build());
        var worker = factory.newWorker(taskQueue);
        for (BaseActivity activity : activities) {
            worker.registerActivitiesImplementations(activity);
        }
        factory.start();
        log.info("Temporal Worker started — namespace={}, taskQueue={}, activities={}",
                namespace, taskQueue, activities.size());
        return factory;
    }
}
