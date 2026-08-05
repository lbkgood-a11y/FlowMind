package com.triobase.service.ops.notification.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * 通知投递的跨语言 Temporal 契约。
 *
 * <p>命令与结果均为标准 JSON；命令必须包含 tenantId 与 taskId。Signal 只改变 Workflow 内的
 * 确定性状态，持久化取消或撤回证据由 Activity 完成。</p>
 */
@WorkflowInterface
public interface NotificationDeliveryWorkflow {

    @WorkflowMethod
    String deliver(String commandJson);

    @SignalMethod
    void cancel(String reason);

    @SignalMethod
    void withdraw(String reason);

    @QueryMethod
    String currentState();
}
