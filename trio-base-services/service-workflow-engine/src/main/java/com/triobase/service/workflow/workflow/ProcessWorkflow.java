package com.triobase.service.workflow.workflow;

import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * 流程实例 Workflow 接口
 *
 * 第二阶段新增：
 * - rejectToNode: 驳回/退回指定节点
 */
@WorkflowInterface
public interface ProcessWorkflow {

    @WorkflowMethod
    void startProcess(ProcessPackageDefinition packageDef,
                      String instanceId,
                      String initiatorId,
                      String initiatorName,
                      String formDataJson);

    @SignalMethod
    void approveTask(String taskId, String action, String userId, String userName, String comment);

    /**
     * 驳回 Signal — 退回指定节点
     * @param taskId 当前待办任务 ID
     * @param targetNodeId 目标节点 ID（退回到的节点）
     * @param comment 驳回理由
     */
    @SignalMethod
    void rejectToNode(String taskId, String targetNodeId, String comment);
}
