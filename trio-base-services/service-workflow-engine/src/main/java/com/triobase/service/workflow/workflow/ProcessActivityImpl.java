package com.triobase.service.workflow.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.dto.internal.ResolvedUserDto;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.dto.ResolvedParticipants;
import com.triobase.service.workflow.dto.ConditionEvaluationResult;
import com.triobase.service.workflow.entity.NodeRecord;
import com.triobase.service.workflow.entity.ParticipantResolution;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.Task;
import com.triobase.service.workflow.entity.TaskCandidate;
import com.triobase.service.workflow.exception.ParticipantResolutionException;
import com.triobase.service.workflow.mapper.NodeRecordMapper;
import com.triobase.service.workflow.mapper.ParticipantResolutionMapper;
import com.triobase.service.workflow.mapper.ProcessInstanceMapper;
import com.triobase.service.workflow.mapper.TaskCandidateMapper;
import com.triobase.service.workflow.mapper.TaskMapper;
import com.triobase.service.workflow.service.ParticipantResolver;
import com.triobase.service.workflow.service.RestrictedConditionEvaluator;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ActivityImpl(taskQueues = "service-workflow-engine")
public class ProcessActivityImpl implements ProcessActivity {

    private static final String PENDING = "PENDING";

    private final ObjectMapper objectMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final TaskMapper taskMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final NodeRecordMapper nodeRecordMapper;
    private final ParticipantResolutionMapper participantResolutionMapper;
    private final ParticipantResolver participantResolver;
    private final RestrictedConditionEvaluator conditionEvaluator;

    @Override
    public String resolveAssignee(ProcessPackageDefinition.Assignment assignment,
                                  String instanceId,
                                  String nodeId,
                                  String participantVersion) {
        String assignmentVersion = participantResolver.participantVersion(assignment);
        String effectiveVersion = participantVersion + ":" + assignmentVersion;
        String resolutionKey = instanceId + ":" + nodeId + ":" + effectiveVersion;
        ParticipantResolution existing = participantResolutionMapper.selectOne(
                new LambdaQueryWrapper<ParticipantResolution>()
                        .eq(ParticipantResolution::getResolutionKey, resolutionKey)
                        .last("LIMIT 1"));
        if (existing != null) {
            persistNodeSnapshot(instanceId, nodeId, existing.getParticipantsJson());
            ensureEligibleParticipants(instanceId, nodeId, existing.getParticipantsJson());
            return existing.getParticipantsJson();
        }

        ResolvedParticipants participants = participantResolver.resolve(assignment);
        participants.setParticipantVersion(effectiveVersion);
        String participantsJson = writeJson(participants);

        ParticipantResolution resolution = new ParticipantResolution();
        resolution.setId(UlidGenerator.nextUlid());
        resolution.setResolutionKey(resolutionKey);
        resolution.setProcessInstanceId(instanceId);
        resolution.setNodeId(nodeId);
        resolution.setAssignmentType(participants.getAssignmentType());
        resolution.setAssignmentRef(participants.getAssignmentRef());
        resolution.setParticipantVersion(participants.getParticipantVersion());
        resolution.setParticipantsJson(participantsJson);

        try {
            participantResolutionMapper.insert(resolution);
        } catch (DuplicateKeyException duplicate) {
            ParticipantResolution persisted = participantResolutionMapper.selectOne(
                    new LambdaQueryWrapper<ParticipantResolution>()
                            .eq(ParticipantResolution::getResolutionKey, resolutionKey)
                            .last("LIMIT 1"));
            if (persisted == null) {
                throw duplicate;
            }
            participantsJson = persisted.getParticipantsJson();
        }

        persistNodeSnapshot(instanceId, nodeId, participantsJson);
        ensureEligibleParticipants(instanceId, nodeId, participantsJson);
        log.info("Resolved participants: instanceId={}, nodeId={}, count={}",
                instanceId, nodeId, readParticipants(participantsJson).getUsers().size());
        return participantsJson;
    }

    @Override
    public String createTask(String instanceId, String nodeId, String nodeName,
                             String nodeType, int visitNo, String assigneeJson) {
        ResolvedParticipants participants = readParticipants(assigneeJson);
        if (participants.getUsers().isEmpty()) {
            throw new ParticipantResolutionException("NO_ELIGIBLE_PARTICIPANTS");
        }

        Task existing = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getProcessInstanceId, instanceId)
                .eq(Task::getNodeId, nodeId)
                .eq(Task::getNodeVisitNo, visitNo)
                .ne(Task::getStatus, "CANCELLED")
                .last("LIMIT 1"));
        if (existing != null) {
            ensureCandidates(existing.getId(), participants);
            return existing.getId();
        }

        ProcessInstance instance = requireProcessInstance(instanceId);
        Task task = newTask(instance, nodeId, nodeName, nodeType, visitNo);
        task.setAssigneeType(participants.getAssignmentType());
        taskMapper.insert(task);
        ensureCandidates(task.getId(), participants);
        updateCurrentNode(instanceId, nodeId);

        log.info("Candidate-backed task created: id={}, candidateCount={}",
                task.getId(), participants.getUsers().size());
        return task.getId();
    }

    @Override
    public void completeTask(String taskId, String action, String comment) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !PENDING.equals(task.getStatus())) {
            return;
        }
        task.setStatus("APPROVE".equals(action) ? "APPROVED" : "REJECTED");
        task.setComment(comment);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Override
    public ConditionEvaluationResult evaluateCondition(String expression, String formDataJson) {
        return conditionEvaluator.evaluate(expression, formDataJson);
    }

    @Override
    public List<String> createCountersignTasks(String instanceId, String nodeId, String nodeName,
                                               String strategy, int visitNo,
                                               String assigneeListJson) {
        ResolvedParticipants participants = readParticipants(assigneeListJson);
        if (participants.getUsers().isEmpty()) {
            throw new ParticipantResolutionException("NO_ELIGIBLE_PARTICIPANTS");
        }

        ProcessInstance instance = requireProcessInstance(instanceId);
        List<String> taskIds = new ArrayList<>();
        for (ResolvedUserDto user : participants.getUsers()) {
            Task existing = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                    .eq(Task::getProcessInstanceId, instanceId)
                    .eq(Task::getNodeId, nodeId)
                    .eq(Task::getNodeVisitNo, visitNo)
                    .eq(Task::getAssigneeId, user.getUserId())
                    .last("LIMIT 1"));
            if (existing != null) {
                taskIds.add(existing.getId());
                continue;
            }

            Task task = newTask(instance, nodeId, nodeName, "COUNTERSIGN", visitNo);
            task.setAssigneeId(user.getUserId());
            task.setAssigneeName(user.getUsername());
            task.setAssigneeType(participants.getAssignmentType());
            taskMapper.insert(task);
            taskIds.add(task.getId());
        }
        updateCurrentNode(instanceId, nodeId);
        log.info("Countersign tasks created: instanceId={}, nodeId={}, count={}, strategy={}",
                instanceId, nodeId, participants.getUsers().size(), strategy);
        return taskIds;
    }

    @Override
    public int getCountersignTaskCount(String instanceId, String nodeId) {
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getProcessInstanceId, instanceId)
                .eq(Task::getNodeId, nodeId)
                .eq(Task::getStatus, PENDING));
        return count != null ? count.intValue() : 0;
    }

    @Override
    public void completeCountersignTask(String taskId, String status, String comment) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !PENDING.equals(task.getStatus())) {
            return;
        }
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
                .eq(Task::getStatus, PENDING));
        for (Task task : pending) {
            task.setStatus("CANCELLED");
            task.setComment("Countersign condition already satisfied");
            taskMapper.updateById(task);
        }
    }

    @Override
    public void rejectToNode(String instanceId, String currentNodeId,
                             String targetNodeId, String comment) {
        List<Task> pending = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProcessInstanceId, instanceId)
                .eq(Task::getNodeId, currentNodeId)
                .eq(Task::getStatus, PENDING));
        for (Task task : pending) {
            task.setStatus("CANCELLED");
            task.setComment("Returned: " + comment);
            taskMapper.updateById(task);
        }
        updateCurrentNode(instanceId, targetNodeId);
    }

    @Override
    public void transferTask(String taskId, String newAssigneeId, String newAssigneeName) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !PENDING.equals(task.getStatus())) {
            return;
        }
        task.setStatus("TRANSFERRED");
        task.setComment("Transferred to " + newAssigneeName);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        Task newTask = copyPendingTask(task);
        newTask.setAssigneeId(newAssigneeId);
        newTask.setAssigneeName(newAssigneeName);
        newTask.setAssigneeType("USER");
        taskMapper.insert(newTask);
    }

    @Override
    public void addSignTask(String instanceId, String nodeId, String nodeName,
                            String assigneeId, String assigneeName) {
        ProcessInstance instance = requireProcessInstance(instanceId);
        Task task = newTask(instance, nodeId, nodeName, "APPROVAL", 1);
        task.setTitle(nodeName + " - Add sign");
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(assigneeName);
        task.setAssigneeType("USER");
        taskMapper.insert(task);
    }

    @Override
    public void recordNodeEnter(String instanceId, String nodeId, String nodeName,
                                String nodeType, String prevNodeId, int visitNo) {
        NodeRecord existing = findNodeRecord(instanceId, nodeId, visitNo);
        if (existing != null) {
            existing.setStatus("ACTIVE");
            existing.setExitedAt(null);
            nodeRecordMapper.updateById(existing);
            return;
        }

        NodeRecord record = new NodeRecord();
        record.setId(UlidGenerator.nextUlid());
        record.setProcessInstanceId(instanceId);
        record.setNodeId(nodeId);
        record.setNodeName(nodeName);
        record.setNodeType(nodeType);
        record.setVisitNo(visitNo);
        record.setStatus("ACTIVE");
        record.setEnteredAt(LocalDateTime.now());
        nodeRecordMapper.insert(record);
        updateCurrentNode(instanceId, nodeId);
    }

    @Override
    public void recordNodeExit(String instanceId, String nodeId, String resultJson) {
        NodeRecord record = findNodeRecord(instanceId, nodeId);
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
    public void failNode(String instanceId, String nodeId, String reason) {
        NodeRecord record = findNodeRecord(instanceId, nodeId);
        if (record != null) {
            record.setStatus("FAILED");
            record.setResult(writeFailureReason(reason));
            record.setExitedAt(LocalDateTime.now());
            nodeRecordMapper.updateById(record);
        }

        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance != null) {
            instance.setStatus("SUSPENDED");
            processInstanceMapper.updateById(instance);
        }
    }

    @Override
    public void completeProcess(String instanceId) {
        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance == null || "SUSPENDED".equals(instance.getStatus())) {
            return;
        }
        instance.setStatus("COMPLETED");
        instance.setCompletedAt(LocalDateTime.now());
        processInstanceMapper.updateById(instance);
    }

    @Override
    public void terminateProcess(String instanceId, String status, String reason) {
        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return;
        }
        instance.setStatus("TERMINATED");
        instance.setCompletedAt(LocalDateTime.now());
        processInstanceMapper.updateById(instance);

        taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .eq(Task::getProcessInstanceId, instanceId)
                        .eq(Task::getStatus, PENDING))
                .forEach(task -> {
                    task.setStatus("CANCELLED");
                    task.setComment(reason);
                    taskMapper.updateById(task);
                });
    }

    private void ensureEligibleParticipants(String instanceId, String nodeId, String participantsJson) {
        ResolvedParticipants participants = readParticipants(participantsJson);
        if (!participants.getUsers().isEmpty()) {
            return;
        }

        failNode(instanceId, nodeId, "NO_ELIGIBLE_PARTICIPANTS");
        throw new ParticipantResolutionException("NO_ELIGIBLE_PARTICIPANTS");
    }

    private void persistNodeSnapshot(String instanceId, String nodeId, String participantsJson) {
        NodeRecord record = findNodeRecord(instanceId, nodeId);
        if (record != null && record.getAssigneeSnapshot() == null) {
            record.setAssigneeSnapshot(participantsJson);
            nodeRecordMapper.updateById(record);
        }
    }

    private void ensureCandidates(String taskId, ResolvedParticipants participants) {
        for (ResolvedUserDto user : participants.getUsers()) {
            Long existing = taskCandidateMapper.selectCount(new LambdaQueryWrapper<TaskCandidate>()
                    .eq(TaskCandidate::getTaskId, taskId)
                    .eq(TaskCandidate::getUserId, user.getUserId()));
            if (existing != null && existing > 0) {
                continue;
            }

            TaskCandidate candidate = new TaskCandidate();
            candidate.setId(UlidGenerator.nextUlid());
            candidate.setTaskId(taskId);
            candidate.setUserId(user.getUserId());
            candidate.setUsername(user.getUsername());
            candidate.setSourceType(participants.getAssignmentType());
            candidate.setSourceRef(participants.getAssignmentRef());
            try {
                taskCandidateMapper.insert(candidate);
            } catch (DuplicateKeyException duplicate) {
                log.debug("Task candidate already exists: taskId={}, userId={}",
                        taskId, user.getUserId());
            }
        }
    }

    private Task newTask(ProcessInstance instance, String nodeId, String nodeName,
                         String nodeType, int visitNo) {
        Task task = new Task();
        task.setId(UlidGenerator.nextUlid());
        task.setProcessInstanceId(instance.getId());
        task.setProcessKey(instance.getProcessKey());
        task.setProcessName(instance.getProcessName());
        task.setNodeId(nodeId);
        task.setNodeName(nodeName);
        task.setNodeType(nodeType);
        task.setNodeVisitNo(visitNo);
        task.setTitle(instance.getProcessName() + " - " + nodeName);
        task.setStatus(PENDING);
        return task;
    }

    private Task copyPendingTask(Task source) {
        Task task = new Task();
        task.setId(UlidGenerator.nextUlid());
        task.setProcessInstanceId(source.getProcessInstanceId());
        task.setProcessKey(source.getProcessKey());
        task.setProcessName(source.getProcessName());
        task.setNodeId(source.getNodeId());
        task.setNodeName(source.getNodeName());
        task.setNodeType(source.getNodeType());
        task.setNodeVisitNo(source.getNodeVisitNo());
        task.setTitle(source.getTitle());
        task.setStatus(PENDING);
        return task;
    }

    private ProcessInstance requireProcessInstance(String instanceId) {
        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new IllegalStateException("Process instance not found: " + instanceId);
        }
        return instance;
    }

    private NodeRecord findNodeRecord(String instanceId, String nodeId) {
        return nodeRecordMapper.selectOne(new LambdaQueryWrapper<NodeRecord>()
                .eq(NodeRecord::getProcessInstanceId, instanceId)
                .eq(NodeRecord::getNodeId, nodeId)
                .orderByDesc(NodeRecord::getVisitNo)
                .last("LIMIT 1"));
    }

    private NodeRecord findNodeRecord(String instanceId, String nodeId, int visitNo) {
        return nodeRecordMapper.selectOne(new LambdaQueryWrapper<NodeRecord>()
                .eq(NodeRecord::getProcessInstanceId, instanceId)
                .eq(NodeRecord::getNodeId, nodeId)
                .eq(NodeRecord::getVisitNo, visitNo)
                .last("LIMIT 1"));
    }

    private void updateCurrentNode(String instanceId, String nodeId) {
        ProcessInstance update = new ProcessInstance();
        update.setId(instanceId);
        update.setCurrentNodeId(nodeId);
        processInstanceMapper.updateById(update);
    }

    private ResolvedParticipants readParticipants(String json) {
        try {
            ResolvedParticipants participants = objectMapper.readValue(json, ResolvedParticipants.class);
            if (participants.getUsers() == null) {
                participants.setUsers(List.of());
            }
            return participants;
        } catch (Exception e) {
            throw new ParticipantResolutionException("INVALID_PARTICIPANT_SNAPSHOT");
        }
    }

    private String writeJson(ResolvedParticipants participants) {
        try {
            return objectMapper.writeValueAsString(participants);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize participant snapshot", e);
        }
    }

    private String writeFailureReason(String reason) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("reason", reason));
        } catch (Exception e) {
            return "{\"reason\":\"NODE_EXECUTION_FAILED\"}";
        }
    }
}
