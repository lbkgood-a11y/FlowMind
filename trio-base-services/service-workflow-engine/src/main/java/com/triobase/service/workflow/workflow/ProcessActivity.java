package com.triobase.service.workflow.workflow;

import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.dto.ConditionEvaluationResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * 承载流程运行中的全部 I/O 和业务副作用。
 *
 * <p>Workflow 不得直接访问数据库或网络；任务创建、状态写入、参与人解析和条件求值均通过
 * Activity 完成。Temporal 可能重试任一方法，因此实现必须以实例、节点、访问次数或 operationId
 * 组成业务幂等身份，重复执行必须返回等价结果。</p>
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

    // 会签任务属于同一节点访问批次，重试不得重复扩大任务集合。

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

    // 驳回和转办改变持久化任务事实，Activity 实现必须校验当前状态并保持幂等。

    @ActivityMethod
    void rejectToNode(String instanceId, String currentNodeId,
                      String targetNodeId, String comment);

    @ActivityMethod
    void transferTask(String taskId, String newAssigneeId, String newAssigneeName);

    @ActivityMethod
    void addSignTask(String instanceId, String nodeId, String nodeName,
                     String assigneeId, String assigneeName);

    // 生命周期记录是审计事实；相同节点访问次数的重复写入必须合并而非追加。

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
