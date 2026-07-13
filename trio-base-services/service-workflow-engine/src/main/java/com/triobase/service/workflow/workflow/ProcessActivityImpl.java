package com.triobase.service.workflow.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.entity.NodeRecord;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.Task;
import com.triobase.service.workflow.mapper.NodeRecordMapper;
import com.triobase.service.workflow.mapper.ProcessInstanceMapper;
import com.triobase.service.workflow.mapper.TaskMapper;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ActivityImpl(taskQueues = "service-workflow-engine")
public class ProcessActivityImpl implements ProcessActivity {

    private final ObjectMapper objectMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final TaskMapper taskMapper;
    private final NodeRecordMapper nodeRecordMapper;

    @Override
    public String resolveAssignee(ProcessPackageDefinition.Assignment assignment, String instanceId) {
        log.info("resolveAssignee: instanceId={}, type={}, roleCode={}",
                instanceId, assignment != null ? assignment.getType() : null,
                assignment != null ? assignment.getRoleCode() : null);

        Map<String, String> result = new HashMap<>();
        result.put("type", assignment != null ? assignment.getType() : "SYSTEM");
        result.put("resolved", "false");
        if (assignment != null && assignment.getRoleCode() != null) {
            result.put("roleCode", assignment.getRoleCode());
        }

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to serialize assignee result", e);
            return "{\"type\":\"SYSTEM\",\"resolved\":\"false\"}";
        }
    }

    @Override
    public String createTask(String instanceId, String nodeId, String nodeName,
                             String nodeType, String assigneeJson) {
        log.info("createTask: instanceId={}, nodeId={}, nodeName={}", instanceId, nodeId, nodeName);

        Task existing = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getProcessInstanceId, instanceId)
                .eq(Task::getNodeId, nodeId)
                .ne(Task::getStatus, "CANCELLED")
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("Task already exists: id={}", existing.getId());
            return existing.getId();
        }

        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance == null) throw new RuntimeException("Process instance not found: " + instanceId);

        String assigneeId = null;
        String assigneeName = null;
        String assigneeType = "SYSTEM";
        try {
            Map<String, String> assigneeMap = objectMapper.readValue(assigneeJson,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            assigneeType = assigneeMap.getOrDefault("type", "SYSTEM");
        } catch (Exception e) {
            log.warn("Failed to parse assigneeJson, using defaults", e);
        }

        Task task = new Task();
        task.setId(UlidGenerator.nextUlid());
        task.setProcessInstanceId(instanceId);
        task.setProcessKey(instance.getProcessKey());
        task.setProcessName(instance.getProcessName());
        task.setNodeId(nodeId);
        task.setNodeName(nodeName);
        task.setNodeType(nodeType);
        task.setTitle(instance.getProcessName() + " - " + nodeName);
        task.setStatus("PENDING");
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(assigneeName);
        task.setAssigneeType(assigneeType);
        taskMapper.insert(task);

        ProcessInstance update = new ProcessInstance();
        update.setId(instanceId);
        update.setCurrentNodeId(nodeId);
        processInstanceMapper.updateById(update);

        log.info("Task created: id={}", task.getId());
        return task.getId();
    }

    @Override
    public void completeTask(String taskId, String action, String comment) {
        log.info("completeTask: taskId={}, action={}", taskId, action);
        Task task = taskMapper.selectById(taskId);
        if (task == null) { log.warn("Task not found: {}", taskId); return; }
        task.setStatus("APPROVE".equals(action) ? "APPROVED" : "REJECTED");
        task.setComment(comment);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    // ── 会签 ──

    @Override
    public void createCountersignTasks(String instanceId, String nodeId, String nodeName,
                                       String strategy, String assigneeListJson) {
        log.info("createCountersignTasks: instanceId={}, nodeId={}, strategy={}", instanceId, nodeId, strategy);
        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance == null) throw new RuntimeException("Process instance not found: " + instanceId);

        // 模拟创建多人任务：每个参与者创建一个
        // 实际场景应该从 assigneeListJson 解析参与者列表
        String[] assignees = {"user1", "user2", "user3"};

        for (String assigneeId : assignees) {
            // 幂等检查
            Task existing = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                    .eq(Task::getProcessInstanceId, instanceId)
                    .eq(Task::getNodeId, nodeId)
                    .eq(Task::getAssigneeId, assigneeId)
                    .last("LIMIT 1"));
            if (existing != null) continue;

            Task task = new Task();
            task.setId(UlidGenerator.nextUlid());
            task.setProcessInstanceId(instanceId);
            task.setProcessKey(instance.getProcessKey());
            task.setProcessName(instance.getProcessName());
            task.setNodeId(nodeId);
            task.setNodeName(nodeName);
            task.setNodeType("COUNTERSIGN");
            task.setTitle(instance.getProcessName() + " - " + nodeName);
            task.setStatus("PENDING");
            task.setAssigneeId(assigneeId);
            task.setAssigneeName("用户" + assigneeId);
            task.setAssigneeType("USER");
            taskMapper.insert(task);
        }

        ProcessInstance update = new ProcessInstance();
        update.setId(instanceId);
        update.setCurrentNodeId(nodeId);
        processInstanceMapper.updateById(update);
    }

    @Override
    public int getCountersignTaskCount(String instanceId, String nodeId) {
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getProcessInstanceId, instanceId)
                .eq(Task::getNodeId, nodeId)
                .eq(Task::getStatus, "PENDING"));
        return count != null ? count.intValue() : 0;
    }

    @Override
    public void completeCountersignTask(String taskId, String status, String comment) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) return;
        task.setStatus(status);
        task.setComment(comment);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Override
    public void cancelRemainingCountersignTasks(String instanceId, String nodeId) {
        List<Task> pending = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProcessInstanceId, instanceId)
                .eq(Task::getNodeId, nodeId)
                .eq(Task::getStatus, "PENDING"));
        for (Task t : pending) {
            t.setStatus("CANCELLED");
            t.setComment("会签已完成，自动取消");
            taskMapper.updateById(t);
        }
    }

    // ── 驳回/转办/加签 ──

    @Override
    public void rejectToNode(String instanceId, String currentNodeId,
                             String targetNodeId, String comment) {
        log.info("rejectToNode: instanceId={}, from={}, to={}", instanceId, currentNodeId, targetNodeId);

        // 取消当前节点所有待办
        List<Task> pending = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProcessInstanceId, instanceId)
                .eq(Task::getNodeId, currentNodeId)
                .eq(Task::getStatus, "PENDING"));
        for (Task t : pending) {
            t.setStatus("CANCELLED");
            t.setComment("驳回：" + comment);
            taskMapper.updateById(t);
        }

        // 更新实例当前节点
        ProcessInstance update = new ProcessInstance();
        update.setId(instanceId);
        update.setCurrentNodeId(targetNodeId);
        processInstanceMapper.updateById(update);
    }

    @Override
    public void transferTask(String taskId, String newAssigneeId, String newAssigneeName) {
        log.info("transferTask: taskId={}, to={}/{}", taskId, newAssigneeId, newAssigneeName);
        Task task = taskMapper.selectById(taskId);
        if (task == null) return;
        task.setAssigneeId(newAssigneeId);
        task.setAssigneeName(newAssigneeName);
        task.setStatus("TRANSFERRED");
        task.setComment("转办给 " + newAssigneeName);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        // 创建新的待办
        Task newTask = new Task();
        newTask.setId(UlidGenerator.nextUlid());
        newTask.setProcessInstanceId(task.getProcessInstanceId());
        newTask.setProcessKey(task.getProcessKey());
        newTask.setProcessName(task.getProcessName());
        newTask.setNodeId(task.getNodeId());
        newTask.setNodeName(task.getNodeName());
        newTask.setNodeType(task.getNodeType());
        newTask.setTitle(task.getTitle());
        newTask.setStatus("PENDING");
        newTask.setAssigneeId(newAssigneeId);
        newTask.setAssigneeName(newAssigneeName);
        newTask.setAssigneeType("USER");
        taskMapper.insert(newTask);
    }

    @Override
    public void addSignTask(String instanceId, String nodeId, String nodeName,
                            String assigneeId, String assigneeName) {
        log.info("addSignTask: instanceId={}, nodeId={}, assignee={}", instanceId, nodeId, assigneeId);
        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance == null) return;

        Task task = new Task();
        task.setId(UlidGenerator.nextUlid());
        task.setProcessInstanceId(instanceId);
        task.setProcessKey(instance.getProcessKey());
        task.setProcessName(instance.getProcessName() != null ? instance.getProcessName() : instance.getProcessKey());
        task.setNodeId(nodeId);
        task.setNodeName(nodeName);
        task.setNodeType("APPROVAL");
        task.setTitle(nodeName + " - 加签");
        task.setStatus("PENDING");
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(assigneeName);
        task.setAssigneeType("USER");
        taskMapper.insert(task);
    }

    // ── 节点/实例生命周期 ──

    @Override
    public void recordNodeEnter(String instanceId, String nodeId, String nodeName,
                                String nodeType, String prevNodeId) {
        log.info("recordNodeEnter: instanceId={}, nodeId={}", instanceId, nodeId);
        NodeRecord existing = nodeRecordMapper.selectOne(new LambdaQueryWrapper<NodeRecord>()
                .eq(NodeRecord::getProcessInstanceId, instanceId)
                .eq(NodeRecord::getNodeId, nodeId)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setStatus("ACTIVE");
            nodeRecordMapper.updateById(existing);
            return;
        }

        NodeRecord record = new NodeRecord();
        record.setId(UlidGenerator.nextUlid());
        record.setProcessInstanceId(instanceId);
        record.setNodeId(nodeId);
        record.setNodeName(nodeName);
        record.setNodeType(nodeType);
        record.setStatus("ACTIVE");
        record.setEnteredAt(LocalDateTime.now());
        nodeRecordMapper.insert(record);

        ProcessInstance update = new ProcessInstance();
        update.setId(instanceId);
        update.setCurrentNodeId(nodeId);
        processInstanceMapper.updateById(update);
    }

    @Override
    public void recordNodeExit(String instanceId, String nodeId, String resultJson) {
        log.info("recordNodeExit: instanceId={}, nodeId={}", instanceId, nodeId);
        NodeRecord record = nodeRecordMapper.selectOne(new LambdaQueryWrapper<NodeRecord>()
                .eq(NodeRecord::getProcessInstanceId, instanceId)
                .eq(NodeRecord::getNodeId, nodeId)
                .last("LIMIT 1"));
        if (record == null) {
            log.warn("Node record not found: instanceId={}, nodeId={}", instanceId, nodeId);
            return;
        }
        record.setStatus("COMPLETED");
        record.setResult(resultJson);
        record.setExitedAt(LocalDateTime.now());
        nodeRecordMapper.updateById(record);
    }

    @Override
    public void completeProcess(String instanceId) {
        log.info("completeProcess: instanceId={}", instanceId);
        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance == null) return;
        instance.setStatus("COMPLETED");
        instance.setCompletedAt(LocalDateTime.now());
        processInstanceMapper.updateById(instance);
    }

    @Override
    public void terminateProcess(String instanceId, String status, String reason) {
        log.info("terminateProcess: instanceId={}, status={}, reason={}", instanceId, status, reason);
        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance == null) return;
        instance.setStatus("TERMINATED");
        instance.setCompletedAt(LocalDateTime.now());
        processInstanceMapper.updateById(instance);

        taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .eq(Task::getProcessInstanceId, instanceId)
                        .eq(Task::getStatus, "PENDING"))
                .forEach(task -> {
                    task.setStatus("CANCELLED");
                    task.setComment(reason);
                    taskMapper.updateById(task);
                });
    }
}
