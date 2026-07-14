package com.triobase.service.workflow.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.workflow.dto.AddSignTaskCommand;
import com.triobase.service.workflow.dto.ConditionEvaluationResult;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.dto.RejectTaskCommand;
import com.triobase.service.workflow.dto.TaskActionCommand;
import com.triobase.service.workflow.dto.TransferTaskCommand;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@WorkflowImpl(taskQueues = "service-workflow-engine")
public class ProcessWorkflowImpl implements ProcessWorkflow {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .setInitialInterval(Duration.ofSeconds(1))
                    .build())
            .build();
    private final ProcessActivity processActivity =
            Workflow.newActivityStub(ProcessActivity.class, activityOptions);

    private final List<WorkflowCommand> commandQueue = new ArrayList<>();
    private final Set<String> receivedOperationIds = new HashSet<>();

    @Override
    public void startProcess(ProcessPackageDefinition packageDefinition,
                             String instanceId,
                             String initiatorId,
                             String initiatorName,
                             String formDataJson) {
        List<ProcessPackageDefinition.NodeSchema> nodes = packageDefinition.getFlow().getNodes();
        Map<String, ProcessPackageDefinition.NodeSchema> nodeById = new HashMap<>();
        Map<String, Integer> visitCounts = new HashMap<>();
        for (ProcessPackageDefinition.NodeSchema node : nodes) {
            nodeById.put(node.getId(), node);
        }

        String currentNodeId = findStartNode(nodes);
        List<String> visitedNodes = new ArrayList<>();

        while (currentNodeId != null) {
            ProcessPackageDefinition.NodeSchema node = nodeById.get(currentNodeId);
            if (node == null) {
                throw new IllegalStateException("PROCESS_NODE_NOT_FOUND:" + currentNodeId);
            }

            int visitNo = visitCounts.getOrDefault(currentNodeId, 0) + 1;
            visitCounts.put(currentNodeId, visitNo);
            processActivity.recordNodeEnter(
                    instanceId,
                    node.getId(),
                    node.getName() != null ? node.getName() : node.getId(),
                    node.getType(),
                    visitedNodes.isEmpty() ? null : visitedNodes.getLast(),
                    visitNo);

            if ("END".equals(node.getType())) {
                processActivity.recordNodeExit(instanceId, node.getId(), "{\"completed\":true}");
                processActivity.completeProcess(instanceId);
                return;
            }

            currentNodeId = switch (node.getType()) {
                case "START" -> handleAutomaticNode(
                        instanceId, node, packageDefinition, formDataJson, visitedNodes);
                case "APPROVAL" -> handleApprovalNode(
                        instanceId, node, packageDefinition, formDataJson,
                        visitedNodes, visitNo);
                case "COUNTERSIGN" -> handleCountersignNode(
                        instanceId, node, packageDefinition, formDataJson,
                        visitedNodes, visitNo);
                default -> throw new IllegalStateException(
                        "UNSUPPORTED_PROCESS_NODE_TYPE:" + node.getType());
            };
        }
    }

    private String handleAutomaticNode(String instanceId,
                                       ProcessPackageDefinition.NodeSchema node,
                                       ProcessPackageDefinition packageDefinition,
                                       String formDataJson,
                                       List<String> visitedNodes) {
        processActivity.recordNodeExit(instanceId, node.getId(), "{\"autoPass\":true}");
        visitedNodes.add(node.getId());
        return resolveNextNode(instanceId, node, packageDefinition, formDataJson);
    }

    private String handleApprovalNode(String instanceId,
                                      ProcessPackageDefinition.NodeSchema node,
                                      ProcessPackageDefinition packageDefinition,
                                      String formDataJson,
                                      List<String> visitedNodes,
                                      int visitNo) {
        String snapshot = processActivity.resolveAssignee(
                node.getAssignment(), instanceId, node.getId(), "visit-" + visitNo);
        String originalTaskId = processActivity.createTask(
                instanceId, node.getId(), node.getName(), node.getType(), visitNo, snapshot);

        Set<String> requiredTaskIds = new LinkedHashSet<>();
        requiredTaskIds.add(originalTaskId);
        List<String> approvedTaskIds = new ArrayList<>();

        while (!requiredTaskIds.isEmpty()) {
            WorkflowCommand command = awaitRelevantCommand(requiredTaskIds);
            switch (command.type()) {
                case "APPROVE" -> {
                    processActivity.completeTask(
                            command.taskId(), "APPROVE", command.comment());
                    requiredTaskIds.remove(command.taskId());
                    approvedTaskIds.add(command.taskId());
                }
                case "REJECT" -> {
                    processActivity.completeTask(
                            command.taskId(), "REJECT", command.comment());
                    return handleRejection(
                            instanceId, node, visitedNodes, command, approvedTaskIds.size());
                }
                case "TRANSFER" -> {
                    requiredTaskIds.remove(command.taskId());
                    requiredTaskIds.add(command.targetTaskId());
                }
                case "ADD_SIGN" -> requiredTaskIds.add(command.targetTaskId());
                default -> throw new IllegalStateException(
                        "UNSUPPORTED_WORKFLOW_COMMAND:" + command.type());
            }
        }

        processActivity.recordNodeExit(
                instanceId,
                node.getId(),
                writeJson(Map.of(
                        "result", "APPROVED",
                        "approvedTaskIds", approvedTaskIds)));
        visitedNodes.add(node.getId());
        return resolveNextNode(instanceId, node, packageDefinition, formDataJson);
    }

    private String handleCountersignNode(String instanceId,
                                         ProcessPackageDefinition.NodeSchema node,
                                         ProcessPackageDefinition packageDefinition,
                                         String formDataJson,
                                         List<String> visitedNodes,
                                         int visitNo) {
        String strategy = node.getStrategy();
        String snapshot = processActivity.resolveAssignee(
                node.getAssignment(), instanceId, node.getId(), "visit-" + visitNo);
        List<String> createdTaskIds = processActivity.createCountersignTasks(
                instanceId, node.getId(), node.getName(), strategy, visitNo, snapshot);
        Set<String> pendingTaskIds = new LinkedHashSet<>(createdTaskIds);
        int approved = 0;
        int rejected = 0;

        while (!pendingTaskIds.isEmpty()) {
            WorkflowCommand command = awaitRelevantCommand(pendingTaskIds);
            if ("REJECT".equals(command.type())) {
                processActivity.completeCountersignTask(
                        command.taskId(), "REJECTED", command.comment());
                pendingTaskIds.remove(command.taskId());
                rejected++;

                if (command.targetNodeId() != null) {
                    return handleRejection(
                            instanceId, node, visitedNodes, command, approved);
                }
                if ("ALL".equals(strategy) || pendingTaskIds.isEmpty()) {
                    processActivity.cancelRemainingCountersignTasks(instanceId, node.getId());
                    processActivity.recordNodeExit(
                            instanceId,
                            node.getId(),
                            countersignResult("REJECTED", approved, rejected, createdTaskIds.size()));
                    processActivity.terminateProcess(
                            instanceId, "REJECTED", "COUNTERSIGN_REJECTED");
                    return null;
                }
                continue;
            }

            if (!"APPROVE".equals(command.type())) {
                throw new IllegalStateException(
                        "COUNTERSIGN_COMMAND_NOT_SUPPORTED:" + command.type());
            }
            processActivity.completeCountersignTask(
                    command.taskId(), "APPROVED", command.comment());
            pendingTaskIds.remove(command.taskId());
            approved++;

            if ("ANY".equals(strategy)) {
                processActivity.cancelRemainingCountersignTasks(instanceId, node.getId());
                pendingTaskIds.clear();
            }
        }

        processActivity.recordNodeExit(
                instanceId,
                node.getId(),
                countersignResult("APPROVED", approved, rejected, createdTaskIds.size()));
        visitedNodes.add(node.getId());
        return resolveNextNode(instanceId, node, packageDefinition, formDataJson);
    }

    private String handleRejection(String instanceId,
                                   ProcessPackageDefinition.NodeSchema node,
                                   List<String> visitedNodes,
                                   WorkflowCommand command,
                                   int approvedCount) {
        String targetNodeId = command.targetNodeId();
        processActivity.recordNodeExit(
                instanceId,
                node.getId(),
                writeJson(Map.of(
                        "result", targetNodeId == null ? "REJECTED" : "RETURNED",
                        "operationId", command.operationId(),
                        "approvedCount", approvedCount,
                        "targetNodeId", targetNodeId != null ? targetNodeId : "")));

        if (targetNodeId == null) {
            processActivity.terminateProcess(instanceId, "REJECTED", command.comment());
            return null;
        }
        if (!visitedNodes.contains(targetNodeId)) {
            String reason = "REJECT_TARGET_NOT_VISITED:" + targetNodeId;
            processActivity.failNode(instanceId, node.getId(), reason);
            throw new IllegalStateException(reason);
        }

        processActivity.rejectToNode(
                instanceId, node.getId(), targetNodeId, command.comment());
        visitedNodes.add(node.getId());
        return targetNodeId;
    }

    private WorkflowCommand awaitRelevantCommand(Set<String> requiredTaskIds) {
        while (true) {
            Workflow.await(() -> !commandQueue.isEmpty());
            WorkflowCommand command = commandQueue.removeFirst();
            if (requiredTaskIds.contains(command.taskId())) {
                return command;
            }
            log.warn("Ignoring command for inactive task: operationId={}, taskId={}",
                    command.operationId(), command.taskId());
        }
    }

    private String resolveNextNode(String instanceId,
                                   ProcessPackageDefinition.NodeSchema node,
                                   ProcessPackageDefinition packageDefinition,
                                   String formDataJson) {
        List<ProcessPackageDefinition.NextCondition> next = node.getNext();
        if (next == null || next.isEmpty()) {
            List<ProcessPackageDefinition.NodeSchema> nodes = packageDefinition.getFlow().getNodes();
            int index = indexOf(nodes, node.getId());
            return index >= 0 && index + 1 < nodes.size()
                    ? nodes.get(index + 1).getId() : null;
        }

        for (ProcessPackageDefinition.NextCondition condition : next) {
            ConditionEvaluationResult result = processActivity.evaluateCondition(
                    condition.getCondition(), formDataJson);
            if ("ERROR".equals(result.getStatus())) {
                String reason = "CONDITION_EVALUATION_FAILED:" + result.getErrorCode();
                processActivity.failNode(instanceId, node.getId(), reason);
                throw new IllegalStateException(reason);
            }
            if (result.isMatched()) {
                return condition.getTarget();
            }
        }

        String reason = "NO_CONDITION_BRANCH_MATCHED";
        processActivity.failNode(instanceId, node.getId(), reason);
        throw new IllegalStateException(reason);
    }

    @Override
    public void approveTask(TaskActionCommand command) {
        enqueue(new WorkflowCommand(
                "APPROVE",
                command.getOperationId(),
                command.getTaskId(),
                null,
                null,
                command.getComment(),
                command.getTraceId()));
    }

    @Override
    public void rejectTask(RejectTaskCommand command) {
        enqueue(new WorkflowCommand(
                "REJECT",
                command.getOperationId(),
                command.getTaskId(),
                null,
                command.getTargetNodeId(),
                command.getComment(),
                command.getTraceId()));
    }

    @Override
    public void transferTask(TransferTaskCommand command) {
        enqueue(new WorkflowCommand(
                "TRANSFER",
                command.getOperationId(),
                command.getSourceTaskId(),
                command.getTargetTaskId(),
                null,
                null,
                command.getTraceId()));
    }

    @Override
    public void addSignTask(AddSignTaskCommand command) {
        enqueue(new WorkflowCommand(
                "ADD_SIGN",
                command.getOperationId(),
                command.getSourceTaskId(),
                command.getAddedTaskId(),
                null,
                null,
                command.getTraceId()));
    }

    private void enqueue(WorkflowCommand command) {
        if (receivedOperationIds.add(command.operationId())) {
            commandQueue.add(command);
        }
    }

    private String findStartNode(List<ProcessPackageDefinition.NodeSchema> nodes) {
        return nodes.stream()
                .filter(node -> "START".equals(node.getType()))
                .findFirst()
                .map(ProcessPackageDefinition.NodeSchema::getId)
                .orElseThrow(() -> new IllegalStateException("PROCESS_START_NODE_NOT_FOUND"));
    }

    private int indexOf(List<ProcessPackageDefinition.NodeSchema> nodes, String nodeId) {
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).getId().equals(nodeId)) {
                return index;
            }
        }
        return -1;
    }

    private String countersignResult(String result, int approved, int rejected, int total) {
        return writeJson(Map.of(
                "result", result,
                "approved", approved,
                "rejected", rejected,
                "total", total));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("WORKFLOW_RESULT_SERIALIZATION_FAILED", e);
        }
    }

    private record WorkflowCommand(
            String type,
            String operationId,
            String taskId,
            String targetTaskId,
            String targetNodeId,
            String comment,
            String traceId) {
    }
}
