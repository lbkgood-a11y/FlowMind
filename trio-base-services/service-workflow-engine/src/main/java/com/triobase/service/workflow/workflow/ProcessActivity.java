package com.triobase.service.workflow.workflow;

import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.dto.ConditionEvaluationResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * 流程 Activity 接口 — 铁律 7：所有 Activity 必须幂等。
 *
 * 第二阶段新增：
 * - createCountersignTasks / getCountersignTaskCount
 * - completeCountersignTask / cancelRemainingCountersignTasks
 * - rejectToNode
 */
@ActivityInterface
public interface ProcessActivity {

    @ActivityMethod
    String resolveAssignee(ProcessPackageDefinition.Assignment assignment,
                           String instanceId,
                           String nodeId,
                           String participantVersion);


        @ActivityMethod
    String createTask(String instanceId, String nodeId, String nodeName,
                      String nodeType, int visitNo, String assigneeJson);

    @ActivityMethod
    void completeTask(String taskId, String action, String comment);

    @ActivityMethod
    ConditionEvaluationResult evaluateCondition(String expression, String formDataJson);

    // ── 会签 ──

    @ActivityMethod
    java.util.List<String> createCountersignTasks(String instanceId, String nodeId, String nodeName,
                                                  String strategy, int visitNo,
                                                  String assigneeListJson);

    @ActivityMethod
    int getCountersignTaskCount(String instanceId, String nodeId);

    @ActivityMethod
    void completeCountersignTask(String taskId, String status, String comment);

    @ActivityMethod
    void cancelRemainingCountersignTasks(String instanceId, String nodeId);

    // ── 驳回/转办 ──

    @ActivityMethod
    void rejectToNode(String instanceId, String currentNodeId,
                      String targetNodeId, String comment);

    @ActivityMethod
    void transferTask(String taskId, String newAssigneeId, String newAssigneeName);

    @ActivityMethod
    void addSignTask(String instanceId, String nodeId, String nodeName,
                     String assigneeId, String assigneeName);

    // ── 节点/实例生命周期 ──

    @ActivityMethod
    void recordNodeEnter(String instanceId, String nodeId, String nodeName,
                         String nodeType, String prevNodeId, int visitNo);

    @ActivityMethod
    void recordNodeExit(String instanceId, String nodeId, String resultJson);

    @ActivityMethod
    void failNode(String instanceId, String nodeId, String reason);

    @ActivityMethod
    void completeProcess(String instanceId);

    @ActivityMethod
    void terminateProcess(String instanceId, String status, String reason);
}
