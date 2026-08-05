package com.triobase.service.ops.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.ops.notification.entity.NotificationTaskEntity;
import com.triobase.service.ops.notification.temporal.NotificationDeliveryWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * 以稳定 Workflow ID 启动通知编排。
 *
 * <p>启动必须发生在数据库事务提交后，避免 Worker 看见尚未提交的任务。Kafka 重放或并发重试
 * 产生相同 Workflow ID 时视为幂等成功，不创建第二条投递链。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationWorkflowLauncher {

    private final WorkflowClient workflowClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name}")
    private String taskQueue;

    public void launchAfterCommit(NotificationTaskEntity task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    launch(task);
                }
            });
            return;
        }
        launch(task);
    }

    public void launch(NotificationTaskEntity task) {
        NotificationDeliveryWorkflow workflow = workflowClient.newWorkflowStub(
                NotificationDeliveryWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(taskQueue)
                        .setWorkflowId(workflowId(task))
                        .build());
        try {
            WorkflowClient.start(workflow::deliver, command(task));
        } catch (WorkflowExecutionAlreadyStarted replay) {
            // 稳定 ID 将 Kafka 重放和并发人工重试折叠为同一条 Workflow 历史。
        }
    }

    private String workflowId(NotificationTaskEntity task) {
        return "notification:" + task.getTenantId() + ":" + task.getId();
    }

    private String command(NotificationTaskEntity task) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "tenantId", task.getTenantId(),
                    "taskId", task.getId(),
                    "traceId", task.getTraceId() == null ? "" : task.getTraceId()));
        } catch (JsonProcessingException exception) {
            throw new BizException(45412, "NOTIFICATION_WORKFLOW_COMMAND_INVALID");
        }
    }
}
