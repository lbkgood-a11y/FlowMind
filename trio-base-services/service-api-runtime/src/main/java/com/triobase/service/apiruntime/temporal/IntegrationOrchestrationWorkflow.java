package com.triobase.service.apiruntime.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * 外部集成编排的跨语言稳定 Workflow 契约。
 *
 * <p>命令、Signal、查询结果统一使用标准 JSON，避免 Java 特有序列化进入 Temporal 历史。
 * Signal 必须包含稳定业务身份，Activity 负责外部 I/O 和幂等副作用。</p>
 */
@WorkflowInterface
public interface IntegrationOrchestrationWorkflow {

    @WorkflowMethod
    String run(String commandJson);

    @SignalMethod
    void receiveSignal(String signalJson);

    @SignalMethod
    void requestCancel(String reason);

    @QueryMethod
    String status();

    @QueryMethod
    String result();
}
