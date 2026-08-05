package com.triobase.service.ops.notification.temporal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.temporal.policy.RetryPolicyPresets;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * 确定性推进通知解析与投影批次。
 *
 * <p>本类不读取系统时间、不生成随机值且不执行 I/O。批次游标和 Signal 状态都进入 Temporal
 * 历史；失败交给具有显式 RetryPolicy 的 Activity 重试，已成功批次依靠业务唯一键防重。</p>
 */
@WorkflowImpl(taskQueues = "service-ops")
public class NotificationDeliveryWorkflowImpl implements NotificationDeliveryWorkflow {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationDeliveryActivities activities = Workflow.newActivityStub(
            NotificationDeliveryActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setHeartbeatTimeout(Duration.ofSeconds(15))
                    .setRetryOptions(RetryPolicyPresets.idempotent())
                    .build());

    private String state = "ACCEPTED";
    private boolean cancellationRequested;
    private boolean withdrawalRequested;
    private String cancellationReason = "CANCELLED_BY_CALLER";
    private String withdrawalReason = "WITHDRAWN_BY_CALLER";

    @Override
    public String deliver(String commandJson) {
        String executionJson = commandJson;
        try {
            state = "RESOLVING";
            executionJson = activities.startResolution(commandJson);
            String terminalState = read(executionJson).path("terminalState").asText();
            if (!terminalState.isBlank()) {
                state = terminalState;
                return executionJson;
            }
            String cursor = null;

            while (true) {
                String terminal = applyRequestedTerminal(executionJson);
                if (terminal != null) {
                    return terminal;
                }

                JsonNode page = read(activities.resolveAudienceBatch(batchCommand(executionJson, cursor)));
                state = "DELIVERING";
                activities.projectBatch(projectionCommand(executionJson, page));
                if (page.path("complete").asBoolean()) {
                    break;
                }
                cursor = page.path("nextCursor").asText();
            }

            String terminal = applyRequestedTerminal(executionJson);
            if (terminal != null) {
                return terminal;
            }
            state = "DELIVERED";
            return activities.completeDelivery(executionJson);
        } catch (RuntimeException failure) {
            state = "FAILED";
            // Activity/数据库详情不得进入 Workflow 结果；运营诊断只持久化稳定分类与安全摘要。
            return activities.failDelivery(failureCommand(executionJson));
        }
    }

    @Override
    public void cancel(String reason) {
        cancellationRequested = true;
        if (reason != null && !reason.isBlank()) {
            cancellationReason = reason;
        }
    }

    @Override
    public void withdraw(String reason) {
        withdrawalRequested = true;
        if (reason != null && !reason.isBlank()) {
            withdrawalReason = reason;
        }
    }

    @Override
    public String currentState() {
        return state;
    }

    private String applyRequestedTerminal(String executionJson) {
        if (withdrawalRequested) {
            state = "WITHDRAWN";
            return activities.withdrawDelivery(reasonCommand(executionJson, withdrawalReason));
        }
        if (cancellationRequested) {
            state = "CANCELLED";
            return activities.cancelDelivery(reasonCommand(executionJson, cancellationReason));
        }
        return null;
    }

    private String batchCommand(String executionJson, String cursor) {
        ObjectNode command = object(executionJson);
        if (cursor == null) {
            command.putNull("cursor");
        } else {
            command.put("cursor", cursor);
        }
        return write(command);
    }

    private String projectionCommand(String executionJson, JsonNode page) {
        ObjectNode command = object(executionJson);
        command.set("recipientUserIds", page.path("recipientUserIds"));
        command.set("checkpoint", page);
        return write(command);
    }

    private String reasonCommand(String executionJson, String reason) {
        ObjectNode command = object(executionJson);
        command.put("reason", reason);
        return write(command);
    }

    private String failureCommand(String executionJson) {
        ObjectNode command = object(executionJson);
        command.put("errorCategory", "DELIVERY_ACTIVITY_FAILED");
        command.put("sanitizedMessage", "Notification delivery could not be completed");
        command.put("retryable", true);
        return write(command);
    }

    private ObjectNode object(String json) {
        JsonNode node = read(json);
        if (!node.isObject()) {
            throw new IllegalArgumentException("NOTIFICATION_WORKFLOW_COMMAND_MUST_BE_OBJECT");
        }
        return (ObjectNode) node.deepCopy();
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("NOTIFICATION_WORKFLOW_JSON_INVALID", exception);
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("NOTIFICATION_WORKFLOW_JSON_INVALID", exception);
        }
    }
}
