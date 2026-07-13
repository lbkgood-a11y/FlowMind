package com.triobase.service.workflow.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程实例 Workflow 实现 — 第二阶段：支持 APPROVAL + COUNTERSIGN + 驳回
 *
 * 铁律 3 遵守：
 * - 使用 Workflow.currentTimeMillis() 而非 System
 * - 不进行任何 I/O（JDBC/Feign/文件）——全部委托给 Activity
 * - 不调用随机数或 UUID
 * - 使用 Workflow.await() 等待 Signal
 */
@Slf4j
@WorkflowImpl(taskQueues = "service-workflow-engine")
public class ProcessWorkflowImpl implements ProcessWorkflow {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ActivityOptions activityOpts = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .setInitialInterval(Duration.ofSeconds(1))
                    .build())
            .build();

    private final ProcessActivity processActivity = Workflow.newActivityStub(ProcessActivity.class, activityOpts);

    // ── Signal 状态 ──
    // 简单节点（单个审批人）
    private String pendingTaskId;
    private volatile boolean signalReceived = false;
    private String signalAction;
    private String signalUserId;
    private String signalUserName;
    private String signalComment;

    // 会签节点（多人审批）
    private String countersignNodeId;
    private int countersignTotalCount;
    private int countersignApproveCount;
    private int countersignRejectCount;
    private volatile boolean countersignComplete = false;
    private String countersignResult; // APPROVED / REJECTED

    // ── 驳回/回退状态 ──
    private volatile boolean rejectionSignalReceived = false;
    private String rejectTargetNodeId;
    @Override
    public void startProcess(ProcessPackageDefinition packageDef,
                             String instanceId,
                             String initiatorId,
                             String initiatorName,
                             String formDataJson) {

        log.info("Workflow[{}] started: processKey={}, initiator={}",
                instanceId, packageDef.getProcessKey(), initiatorName);

        List<ProcessPackageDefinition.NodeSchema> nodes = packageDef.getFlow().getNodes();
        Map<String, ProcessPackageDefinition.NodeSchema> nodeMap = new HashMap<>();

        for (ProcessPackageDefinition.NodeSchema node : nodes) {
            nodeMap.put(node.getId(), node);
        }

        // ── 从 START 节点开始流转 ──
        String currentNodeId = findFirstNode(nodes);
        // 记录所有已走过的节点，用于驳回可选目标
        List<String> visitedNodes = new ArrayList<>();

        while (currentNodeId != null && !"END".equals(getNodeType(nodeMap, currentNodeId))) {
            ProcessPackageDefinition.NodeSchema node = nodeMap.get(currentNodeId);
            if (node == null) break;

            String nodeType = node.getType();
            log.info("Workflow[{}] entering node: {} ({})", instanceId, node.getName(), nodeType);

            processActivity.recordNodeEnter(instanceId, node.getId(), node.getName(), nodeType,
                    visitedNodes.isEmpty() ? null : visitedNodes.get(visitedNodes.size() - 1));

            switch (nodeType) {
                case "START":
                    processActivity.recordNodeExit(instanceId, node.getId(), "{\"autoPass\":true}");
                    visitedNodes.add(currentNodeId);
                    currentNodeId = resolveNextNode(node, packageDef, formDataJson);
                    break;

                case "APPROVAL":
                    currentNodeId = handleApprovalNode(instanceId, node, formDataJson, packageDef, visitedNodes);
                    break;

                case "COUNTERSIGN":
                    currentNodeId = handleCountersignNode(instanceId, node, formDataJson, packageDef, visitedNodes);
                    break;

                default:
                    log.warn("Workflow[{}] unsupported node type: {}", instanceId, nodeType);
                    processActivity.recordNodeExit(instanceId, node.getId(), "{\"skipped\":true}");
                    visitedNodes.add(currentNodeId);
                    currentNodeId = resolveNextNode(node, packageDef, formDataJson);
                    break;
            }

            // 如果节点返回 null，说明流程结束
            if (currentNodeId == null) break;
        }

        // ── 流程完成 ──
        if (currentNodeId != null && "END".equals(getNodeType(nodeMap, currentNodeId))) {
            processActivity.recordNodeEnter(instanceId, currentNodeId, "结束", "END",
                    visitedNodes.isEmpty() ? null : visitedNodes.get(visitedNodes.size() - 1));
            processActivity.recordNodeExit(instanceId, currentNodeId, "{\"completed\":true}");
        }

        processActivity.completeProcess(instanceId);
        log.info("Workflow[{}] completed", instanceId);
    }

    /**
     * 处理普通审批节点
     */
    private String handleApprovalNode(String instanceId,
                                       ProcessPackageDefinition.NodeSchema node,
                                       String formDataJson,
                                       ProcessPackageDefinition packageDef,
                                       List<String> visitedNodes) {
        String assigneeJson = processActivity.resolveAssignee(node.getAssignment(), instanceId);
        pendingTaskId = processActivity.createTask(instanceId, node.getId(),
                node.getName(), node.getType(), assigneeJson);

        // 等待审批 Signal
        resetSignal();
        rejectionSignalReceived = false;
        Workflow.await(() -> signalReceived || rejectionSignalReceived);

        // 处理驳回
        if (rejectionSignalReceived) {
            return handleRejection(instanceId, node, formDataJson, packageDef, visitedNodes);
        }

        // 记录审批结果
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("action", signalAction);
        resultMap.put("comment", signalComment);
        resultMap.put("operator", signalUserName);
        try {
            String resultJson = objectMapper.writeValueAsString(resultMap);
            processActivity.completeTask(pendingTaskId, signalAction, signalComment);
            processActivity.recordNodeExit(instanceId, node.getId(), resultJson);
        } catch (Exception e) {
            log.error("Workflow[{}] task completion failed", instanceId, e);
            throw new RuntimeException("Task completion failed", e);
        }

        // REJECTED → 终止流程
        if ("REJECT".equals(signalAction)) {
            log.info("Workflow[{}] rejected at node: {}", instanceId, node.getName());
            processActivity.terminateProcess(instanceId, "REJECTED", "驳回：" + signalComment);
            return null;
        }

        visitedNodes.add(node.getId());
        return resolveNextNode(node, packageDef, formDataJson);
    }

    /**
     * 处理会签节点（平行会签）
     * ALL = 全部通过 | ANY = 一人通过即可
     */
    private String handleCountersignNode(String instanceId,
                                          ProcessPackageDefinition.NodeSchema node,
                                          String formDataJson,
                                          ProcessPackageDefinition packageDef,
                                          List<String> visitedNodes) {
        String strategy = node.getStrategy() != null ? node.getStrategy() : "ALL";
        int threshold = "ANY".equals(strategy) ? 1 : Integer.MAX_VALUE; // ANY=1票通过, ALL=需要全部

        // 创建会签任务（由 Activity 实现，支持批量参与者解析）
        countersignNodeId = node.getId();
        countersignTotalCount = 0;
        countersignApproveCount = 0;
        countersignRejectCount = 0;
        countersignComplete = false;
        countersignResult = null;

        // 创建会签任务 - 多人任务
        processActivity.createCountersignTasks(instanceId, node.getId(),
                node.getName(), strategy, resolveAssigneeJsonList(node.getAssignment(), instanceId));

        // 等待满足会签条件
        resetSignal();
        rejectionSignalReceived = false;

        // 动态等待：ALL 策略需要总人数确认后再等待足够票数
        // 先通过 Activity 获取总人数
        countersignTotalCount = processActivity.getCountersignTaskCount(instanceId, node.getId());

        if ("ALL".equals(strategy)) {
            // 等待所有人审批
            while (countersignApproveCount + countersignRejectCount < countersignTotalCount) {
                Workflow.await(() -> signalReceived || rejectionSignalReceived);
                if (signalReceived) {
                    if ("APPROVE".equals(signalAction)) {
                        countersignApproveCount++;
                        processActivity.completeCountersignTask(pendingTaskId, "APPROVED", signalComment);
                    } else if ("REJECT".equals(signalAction)) {
                        countersignRejectCount++;
                        processActivity.completeCountersignTask(pendingTaskId, "REJECTED", signalComment);
                        // REJECT → 整个会签驳回
                        log.info("Workflow[{}] countersign rejected at node: {}", instanceId, node.getName());
                        processActivity.recordNodeExit(instanceId, node.getId(),
                                "{\"countersignResult\":\"REJECTED\",\"approved\":" + countersignApproveCount
                                + ",\"total\":" + countersignTotalCount + "}");
                        processActivity.terminateProcess(instanceId, "REJECTED", "会签驳回");
                        return null;
                    }
                    resetSignal();
                } else if (rejectionSignalReceived) {
                    return handleRejection(instanceId, node, formDataJson, packageDef, visitedNodes);
                }
            }
            // 全部通过
            countersignResult = "APPROVED";
        } else {
            // ANY：一人通过即可
            while (countersignApproveCount < threshold
                    && countersignApproveCount + countersignRejectCount < countersignTotalCount) {
                Workflow.await(() -> signalReceived || rejectionSignalReceived);
                if (signalReceived) {
                    if ("APPROVE".equals(signalAction)) {
                        countersignApproveCount++;
                        processActivity.completeCountersignTask(pendingTaskId, "APPROVED", signalComment);
                    } else if ("REJECT".equals(signalAction)) {
                        countersignRejectCount++;
                        processActivity.completeCountersignTask(pendingTaskId, "REJECTED", signalComment);
                    }
                    resetSignal();
                } else if (rejectionSignalReceived) {
                    return handleRejection(instanceId, node, formDataJson, packageDef, visitedNodes);
                }
            }

            if (countersignApproveCount >= threshold) {
                countersignResult = "APPROVED";
                // 取消剩余的代办
                processActivity.cancelRemainingCountersignTasks(instanceId, node.getId());
            } else {
                // 全部驳回
                countersignResult = "REJECTED";
                processActivity.terminateProcess(instanceId, "REJECTED", "会签全部驳回");
                return null;
            }
        }

        processActivity.recordNodeExit(instanceId, node.getId(),
                "{\"countersignResult\":\"" + countersignResult
                + "\",\"approved\":" + countersignApproveCount
                + ",\"total\":" + countersignTotalCount + "}");

        visitedNodes.add(node.getId());
        return resolveNextNode(node, packageDef, formDataJson);
    }

    /**
     * 处理驳回（退回指定节点）
     */
    private String handleRejection(String instanceId,
                                    ProcessPackageDefinition.NodeSchema currentNode,
                                    String formDataJson,
                                    ProcessPackageDefinition packageDef,
                                    List<String> visitedNodes) {
        // 驳回操作由 Activity 取消当前待办，创建被驳回节点的待办
        processActivity.rejectToNode(instanceId, currentNode.getId(), rejectTargetNodeId, signalComment);
        processActivity.recordNodeExit(instanceId, currentNode.getId(),
                "{\"action\":\"REJECT\",\"rejectTo\":\"" + rejectTargetNodeId + "\"}");

        // 重新流转到目标节点
        visitedNodes.add(currentNode.getId());
        return rejectTargetNodeId;
    }

    @Override
    public void approveTask(String taskId, String action, String userId, String userName, String comment) {
        log.info("Signal: taskId={}, action={}, operator={}", taskId, action, userName);
        this.signalAction = action;
        this.signalUserId = userId;
        this.signalUserName = userName;
        this.signalComment = comment;
        this.pendingTaskId = taskId;
        this.signalReceived = true;
    }

    @Override
    public void rejectToNode(String taskId, String targetNodeId, String comment) {
        log.info("Rejection signal: taskId={}, targetNodeId={}", taskId, targetNodeId);
        this.signalAction = "REJECT";
        this.signalComment = comment;
        this.pendingTaskId = taskId;
        this.rejectTargetNodeId = targetNodeId;
        this.rejectionSignalReceived = true;
        this.signalReceived = true;
    }

    // ── 私有方法 ──

    private void resetSignal() {
        signalReceived = false;
        signalAction = null;
        signalUserId = null;
        signalUserName = null;
        signalComment = null;
        pendingTaskId = null;
    }

    private String findFirstNode(List<ProcessPackageDefinition.NodeSchema> nodes) {
        for (ProcessPackageDefinition.NodeSchema node : nodes) {
            if ("START".equals(node.getType())) {
                return node.getId();
            }
        }
        return nodes.isEmpty() ? null : nodes.get(0).getId();
    }

    private String resolveNextNode(ProcessPackageDefinition.NodeSchema node,
                                   ProcessPackageDefinition packageDef,
                                   String formDataJson) {
        List<ProcessPackageDefinition.NextCondition> next = node.getNext();
        if (next == null || next.isEmpty()) {
            // 按定义顺序取下一节点
            List<ProcessPackageDefinition.NodeSchema> nodes = packageDef.getFlow().getNodes();
            int idx = indexOf(nodes, node.getId());
            return (idx >= 0 && idx + 1 < nodes.size()) ? nodes.get(idx + 1).getId() : null;
        }

        // 按条件匹配
        try {
            Map<String, Object> formData = formDataJson != null
                    ? objectMapper.readValue(formDataJson, new TypeReference<Map<String, Object>>() {})
                    : new HashMap<>();

            for (ProcessPackageDefinition.NextCondition nc : next) {
                if ("true".equals(nc.getCondition().trim())) {
                    return nc.getTarget();
                }
            }
        } catch (Exception e) {
            log.error("Condition evaluation failed, using default path", e);
        }

        return next.get(next.size() - 1).getTarget();
    }

    private String resolveAssigneeJsonList(ProcessPackageDefinition.Assignment assignment, String instanceId) {
        if (assignment == null) {
            return "[]";
        }
        return processActivity.resolveAssignee(assignment, instanceId);
    }

    private String getNodeType(Map<String, ProcessPackageDefinition.NodeSchema> nodeMap, String nodeId) {
        ProcessPackageDefinition.NodeSchema node = nodeMap.get(nodeId);
        return node != null ? node.getType() : null;
    }

    private int indexOf(List<ProcessPackageDefinition.NodeSchema> nodes, String nodeId) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getId().equals(nodeId)) return i;
        }
        return -1;
    }
}
