package com.triobase.service.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.common.dto.internal.ResolvedUserDto;
import com.triobase.service.workflow.dto.AddSignRequest;
import com.triobase.service.workflow.dto.AddSignTaskCommand;
import com.triobase.service.workflow.dto.ApproveTaskRequest;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.dto.RejectTaskCommand;
import com.triobase.service.workflow.dto.RejectTaskRequest;
import com.triobase.service.workflow.dto.ResolvedParticipants;
import com.triobase.service.workflow.dto.TaskActionCommand;
import com.triobase.service.workflow.dto.TaskResponse;
import com.triobase.service.workflow.dto.TransferTaskCommand;
import com.triobase.service.workflow.dto.TransferTaskRequest;
import com.triobase.service.workflow.entity.NodeRecord;
import com.triobase.service.workflow.entity.Task;
import com.triobase.service.workflow.entity.TaskOperation;
import com.triobase.service.workflow.mapper.NodeRecordMapper;
import com.triobase.service.workflow.mapper.TaskMapper;
import com.triobase.service.workflow.mapper.TaskOperationMapper;
import com.triobase.service.workflow.workflow.ProcessWorkflow;
import io.temporal.client.WorkflowClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskOperationMapper taskOperationMapper;
    private final NodeRecordMapper nodeRecordMapper;
    private final WorkflowClient workflowClient;
    private final ParticipantResolver participantResolver;

    public PageResult<TaskResponse> myPendingTasks(int pageNo, int pageSize) {
        String userId = requireUserId();
        IPage<Task> page = taskMapper.selectPendingForUser(
                new Page<>(pageNo, pageSize), userId);
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), pageNo, pageSize);
    }

    public PageResult<TaskResponse> myCompletedTasks(int pageNo, int pageSize) {
        String userId = requireUserId();
        IPage<Task> page = taskMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getAssigneeId, userId)
                        .in(Task::getStatus, "APPROVED", "REJECTED", "TRANSFERRED")
                        .orderByDesc(Task::getCompletedAt));
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), pageNo, pageSize);
    }

    public TaskResponse getById(String id) {
        return toResponse(requireTask(id));
    }

    @Transactional
    public TaskResponse approve(String taskId, ApproveTaskRequest request) {
        String action = normalizeApprovalAction(request.getAction());
        if ("REJECT".equals(action)) {
            return rejectInternal(
                    taskId, request.getOperationId(), null, request.getComment());
        }

        String operationId = normalizeOperationId(request.getOperationId());
        ClaimResult claim = claimForOperation(taskId, operationId, "APPROVE");
        if (claim.duplicate()) {
            return toResponse(claim.task());
        }

        Task task = claim.task();
        TaskOperation operation = newOperation(
                operationId, task, "APPROVE", request.getComment());
        if (!insertOperation(operation)) {
            return toResponse(requireTask(taskId));
        }

        completeTaskState(task, "APPROVED", request.getComment());

        TaskActionCommand command = new TaskActionCommand();
        command.setOperationId(operationId);
        command.setTaskId(taskId);
        command.setAction("APPROVE");
        command.setUserId(operation.getOperatorId());
        command.setUserName(operation.getOperatorName());
        command.setComment(request.getComment());
        command.setTraceId(operation.getTraceId());
        workflow(task).approveTask(command);

        log.info("Task approved: taskId={}, operationId={}, operator={}",
                taskId, operationId, operation.getOperatorName());
        return toResponse(task);
    }

    @Transactional
    public TaskResponse reject(String taskId, RejectTaskRequest request) {
        return rejectInternal(
                taskId, request.getOperationId(), request.getTargetNodeId(), request.getComment());
    }

    @Transactional
    public TaskResponse transfer(String taskId, TransferTaskRequest request) {
        String operationId = normalizeOperationId(request.getOperationId());
        ClaimResult claim = claimForOperation(taskId, operationId, "TRANSFER");
        if (claim.duplicate()) {
            return toResponse(claim.task());
        }

        Task sourceTask = claim.task();
        requireApprovalTask(sourceTask, "TRANSFER_ONLY_SUPPORTED_FOR_APPROVAL");
        ResolvedUserDto targetUser = resolveEnabledUser(request.getNewAssigneeId());
        Task targetTask = createLinkedTask(sourceTask, targetUser, "Transfer");

        TaskOperation operation = newOperation(
                operationId, sourceTask, "TRANSFER", null);
        operation.setTargetTaskId(targetTask.getId());
        operation.setTargetUserId(targetUser.getUserId());
        operation.setTargetUserName(targetUser.getUsername());
        if (!insertOperation(operation)) {
            return toResponse(requireTask(taskId));
        }

        sourceTask.setStatus("TRANSFERRED");
        sourceTask.setComment("Transferred to " + targetUser.getUsername());
        sourceTask.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(sourceTask);

        TransferTaskCommand command = new TransferTaskCommand();
        command.setOperationId(operationId);
        command.setSourceTaskId(sourceTask.getId());
        command.setTargetTaskId(targetTask.getId());
        command.setOperatorId(operation.getOperatorId());
        command.setOperatorName(operation.getOperatorName());
        command.setTargetUserId(targetUser.getUserId());
        command.setTargetUserName(targetUser.getUsername());
        command.setTraceId(operation.getTraceId());
        workflow(sourceTask).transferTask(command);

        log.info("Task transferred: taskId={}, operationId={}, target={}",
                taskId, operationId, targetUser.getUserId());
        return toResponse(sourceTask);
    }

    @Transactional
    public TaskResponse addSign(String taskId, AddSignRequest request) {
        String operationId = normalizeOperationId(request.getOperationId());
        ClaimResult claim = claimForOperation(taskId, operationId, "ADD_SIGN");
        if (claim.duplicate()) {
            return toResponse(claim.task());
        }

        Task sourceTask = claim.task();
        requireApprovalTask(sourceTask, "ADD_SIGN_ONLY_SUPPORTED_FOR_APPROVAL");
        ResolvedUserDto targetUser = resolveEnabledUser(request.getAssigneeId());
        Task addedTask = createLinkedTask(sourceTask, targetUser, "Add sign");

        TaskOperation operation = newOperation(
                operationId, sourceTask, "ADD_SIGN", null);
        operation.setTargetTaskId(addedTask.getId());
        operation.setTargetUserId(targetUser.getUserId());
        operation.setTargetUserName(targetUser.getUsername());
        if (!insertOperation(operation)) {
            return toResponse(requireTask(taskId));
        }

        AddSignTaskCommand command = new AddSignTaskCommand();
        command.setOperationId(operationId);
        command.setSourceTaskId(sourceTask.getId());
        command.setAddedTaskId(addedTask.getId());
        command.setOperatorId(operation.getOperatorId());
        command.setOperatorName(operation.getOperatorName());
        command.setTargetUserId(targetUser.getUserId());
        command.setTargetUserName(targetUser.getUsername());
        command.setTraceId(operation.getTraceId());
        workflow(sourceTask).addSignTask(command);

        log.info("Parallel add-sign registered: taskId={}, operationId={}, addedTaskId={}",
                taskId, operationId, addedTask.getId());
        return toResponse(sourceTask);
    }

    public List<String> getRejectTargets(String processInstanceId) {
        return nodeRecordMapper.selectList(new LambdaQueryWrapper<NodeRecord>()
                        .eq(NodeRecord::getProcessInstanceId, processInstanceId)
                        .eq(NodeRecord::getStatus, "COMPLETED")
                        .in(NodeRecord::getNodeType, "APPROVAL", "COUNTERSIGN")
                        .orderByAsc(NodeRecord::getEnteredAt))
                .stream()
                .map(NodeRecord::getNodeId)
                .distinct()
                .toList();
    }

    private TaskResponse rejectInternal(String taskId, String requestedOperationId,
                                        String targetNodeId, String comment) {
        String operationId = normalizeOperationId(requestedOperationId);
        String normalizedTarget = StringUtils.hasText(targetNodeId) ? targetNodeId.trim() : null;
        String operationAction = normalizedTarget != null ? "RETURN" : "REJECT";
        ClaimResult claim = claimForOperation(taskId, operationId, operationAction);
        if (claim.duplicate()) {
            return toResponse(claim.task());
        }

        Task task = claim.task();
        if (normalizedTarget != null) {
            validateRejectTarget(task, normalizedTarget);
        }

        TaskOperation operation = newOperation(
                operationId, task, operationAction, comment);
        operation.setTargetNodeId(normalizedTarget);
        if (!insertOperation(operation)) {
            return toResponse(requireTask(taskId));
        }

        completeTaskState(task, "REJECTED", comment);

        RejectTaskCommand command = new RejectTaskCommand();
        command.setOperationId(operationId);
        command.setTaskId(taskId);
        command.setUserId(operation.getOperatorId());
        command.setUserName(operation.getOperatorName());
        command.setTargetNodeId(normalizedTarget);
        command.setComment(comment);
        command.setTraceId(operation.getTraceId());
        workflow(task).rejectTask(command);

        log.info("Task rejected: taskId={}, operationId={}, targetNode={}",
                taskId, operationId, normalizedTarget);
        return toResponse(task);
    }

    private ClaimResult claimForOperation(String taskId, String operationId, String action) {
        TaskOperation existing = findOperation(operationId);
        if (existing != null) {
            validateExistingOperation(existing, taskId, action);
            return new ClaimResult(requireTask(taskId), true);
        }

        String userId = requireUserId();
        int claimed = taskMapper.claimPendingTask(taskId, userId, effectiveUsername(userId));
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(40400, "TASK_NOT_FOUND");
        }
        if (claimed == 0) {
            existing = findOperation(operationId);
            if (existing != null) {
                validateExistingOperation(existing, taskId, action);
                return new ClaimResult(task, true);
            }
            if (!"PENDING".equals(task.getStatus())) {
                throw new BizException(40000, "TASK_ALREADY_PROCESSED");
            }
            if (task.getAssigneeId() != null && !userId.equals(task.getAssigneeId())) {
                throw new BizException(40300, "TASK_CLAIMED_BY_ANOTHER_USER");
            }
            throw new BizException(40300, "TASK_NOT_CANDIDATE");
        }

        existing = findOperation(operationId);
        if (existing != null) {
            validateExistingOperation(existing, taskId, action);
            return new ClaimResult(task, true);
        }
        return new ClaimResult(task, false);
    }

    private TaskOperation newOperation(String operationId, Task task,
                                       String action, String comment) {
        String userId = requireUserId();
        TaskOperation operation = new TaskOperation();
        operation.setId(UlidGenerator.nextUlid());
        operation.setOperationId(operationId);
        operation.setProcessInstanceId(task.getProcessInstanceId());
        operation.setSourceTaskId(task.getId());
        operation.setAction(action);
        operation.setOperatorId(userId);
        operation.setOperatorName(effectiveUsername(userId));
        operation.setComment(comment);
        operation.setStatus("ACCEPTED");
        operation.setTraceId(TraceUtil.getTraceId());
        return operation;
    }

    private boolean insertOperation(TaskOperation operation) {
        int inserted = taskOperationMapper.insertIfAbsent(operation);
        if (inserted == 1) {
            return true;
        }
        TaskOperation existing = findOperation(operation.getOperationId());
        if (existing == null) {
            throw new BizException(40900, "TASK_OPERATION_CONFLICT");
        }
        validateExistingOperation(
                existing, operation.getSourceTaskId(), operation.getAction());
        return false;
    }

    private TaskOperation findOperation(String operationId) {
        return taskOperationMapper.selectOne(new LambdaQueryWrapper<TaskOperation>()
                .eq(TaskOperation::getOperationId, operationId)
                .last("LIMIT 1"));
    }

    private void validateExistingOperation(TaskOperation operation,
                                           String taskId,
                                           String action) {
        if (!taskId.equals(operation.getSourceTaskId())
                || !action.equals(operation.getAction())) {
            throw new BizException(40900, "OPERATION_ID_CONFLICT");
        }
    }

    private void validateRejectTarget(Task task, String targetNodeId) {
        if (targetNodeId.equals(task.getNodeId())) {
            throw new BizException(40000, "REJECT_TARGET_MUST_BE_PREVIOUS_NODE");
        }
        Long visited = nodeRecordMapper.selectCount(new LambdaQueryWrapper<NodeRecord>()
                .eq(NodeRecord::getProcessInstanceId, task.getProcessInstanceId())
                .eq(NodeRecord::getNodeId, targetNodeId)
                .eq(NodeRecord::getStatus, "COMPLETED")
                .in(NodeRecord::getNodeType, "APPROVAL", "COUNTERSIGN"));
        if (visited == null || visited == 0) {
            throw new BizException(40000, "REJECT_TARGET_NOT_VISITED");
        }
    }

    private ResolvedUserDto resolveEnabledUser(String userId) {
        ProcessPackageDefinition.Assignment assignment = new ProcessPackageDefinition.Assignment();
        assignment.setType("USER");
        assignment.setUserId(userId);
        ResolvedParticipants participants = participantResolver.resolve(assignment);
        if (participants.getUsers().isEmpty()) {
            throw new BizException(40000, "TARGET_USER_NOT_ENABLED");
        }
        return participants.getUsers().getFirst();
    }

    private Task createLinkedTask(Task source, ResolvedUserDto targetUser, String suffix) {
        Task task = new Task();
        task.setId(UlidGenerator.nextUlid());
        task.setProcessInstanceId(source.getProcessInstanceId());
        task.setProcessKey(source.getProcessKey());
        task.setProcessName(source.getProcessName());
        task.setNodeId(source.getNodeId());
        task.setNodeName(source.getNodeName());
        task.setNodeType(source.getNodeType());
        task.setNodeVisitNo(source.getNodeVisitNo());
        task.setTitle(source.getTitle() + " - " + suffix);
        task.setStatus("PENDING");
        task.setAssigneeId(targetUser.getUserId());
        task.setAssigneeName(targetUser.getUsername());
        task.setAssigneeType("USER");
        task.setSourceTaskId(source.getId());
        task.setRootTaskId(source.getRootTaskId() != null
                ? source.getRootTaskId() : source.getId());
        taskMapper.insert(task);
        return task;
    }

    private void completeTaskState(Task task, String status, String comment) {
        task.setStatus(status);
        task.setComment(comment);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void requireApprovalTask(Task task, String errorCode) {
        if (!"APPROVAL".equals(task.getNodeType())) {
            throw new BizException(40000, errorCode);
        }
    }

    private ProcessWorkflow workflow(Task task) {
        return workflowClient.newWorkflowStub(
                ProcessWorkflow.class, "process-" + task.getProcessInstanceId());
    }

    private Task requireTask(String taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(40400, "TASK_NOT_FOUND");
        }
        return task;
    }

    private String requireUserId() {
        String userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(40100, "UNAUTHENTICATED");
        }
        return userId;
    }

    private String effectiveUsername(String userId) {
        String username = SecurityContextHolder.getUsername();
        return StringUtils.hasText(username) ? username : userId;
    }

    private String normalizeOperationId(String operationId) {
        String normalized = StringUtils.hasText(operationId)
                ? operationId.trim() : UlidGenerator.nextUlid();
        if (normalized.length() > 64) {
            throw new BizException(40000, "OPERATION_ID_TOO_LONG");
        }
        return normalized;
    }

    private String normalizeApprovalAction(String action) {
        String normalized = StringUtils.hasText(action)
                ? action.trim().toUpperCase(Locale.ROOT) : "APPROVE";
        if (!"APPROVE".equals(normalized) && !"REJECT".equals(normalized)) {
            throw new BizException(40000, "INVALID_TASK_ACTION");
        }
        return normalized;
    }

    private TaskResponse toResponse(Task task) {
        TaskResponse response = new TaskResponse();
        response.setId(task.getId());
        response.setProcessInstanceId(task.getProcessInstanceId());
        response.setProcessKey(task.getProcessKey());
        response.setProcessName(task.getProcessName());
        response.setNodeId(task.getNodeId());
        response.setNodeName(task.getNodeName());
        response.setNodeType(task.getNodeType());
        response.setNodeVisitNo(task.getNodeVisitNo());
        response.setTitle(task.getTitle());
        response.setStatus(task.getStatus());
        response.setAssigneeId(task.getAssigneeId());
        response.setAssigneeName(task.getAssigneeName());
        response.setAssigneeType(task.getAssigneeType());
        response.setSourceTaskId(task.getSourceTaskId());
        response.setRootTaskId(task.getRootTaskId());
        response.setComment(task.getComment());
        response.setClaimedAt(task.getClaimedAt());
        response.setCompletedAt(task.getCompletedAt());
        response.setCreatedAt(task.getCreatedAt());
        return response;
    }

    private record ClaimResult(Task task, boolean duplicate) {
    }
}
