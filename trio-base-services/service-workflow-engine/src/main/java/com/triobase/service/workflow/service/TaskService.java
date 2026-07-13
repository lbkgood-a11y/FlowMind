package com.triobase.service.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.workflow.dto.AddSignRequest;
import com.triobase.service.workflow.dto.ApproveTaskRequest;
import com.triobase.service.workflow.dto.RejectTaskRequest;
import com.triobase.service.workflow.dto.TransferTaskRequest;
import com.triobase.service.workflow.dto.TaskResponse;
import com.triobase.service.workflow.entity.Task;
import com.triobase.service.workflow.mapper.TaskMapper;
import com.triobase.service.workflow.workflow.ProcessActivity;
import com.triobase.service.workflow.workflow.ProcessWorkflow;
import io.temporal.client.WorkflowClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final WorkflowClient workflowClient;
    private final ProcessActivity processActivity;

    public PageResult<TaskResponse> myPendingTasks(int pageNo, int pageSize) {
        String userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(40100, "UNAUTHENTICATED");
        }

        IPage<Task> page = taskMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getAssigneeId, userId)
                        .eq(Task::getStatus, "PENDING")
                        .orderByDesc(Task::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), pageNo, pageSize);
    }

    public PageResult<TaskResponse> myCompletedTasks(int pageNo, int pageSize) {
        String userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(40100, "UNAUTHENTICATED");
        }

        IPage<Task> page = taskMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getAssigneeId, userId)
                        .in(Task::getStatus, "APPROVED", "REJECTED")
                        .orderByDesc(Task::getCompletedAt));
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), pageNo, pageSize);
    }

    public TaskResponse getById(String id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(40400, "TASK_NOT_FOUND");
        }
        return toResponse(task);
    }

    @Transactional
    public TaskResponse approve(String taskId, ApproveTaskRequest request) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new BizException(40400, "TASK_NOT_FOUND");
        if (!"PENDING".equals(task.getStatus())) throw new BizException(40000, "TASK_ALREADY_PROCESSED");

        String userId = SecurityContextHolder.getUserId();
        String userName = SecurityContextHolder.getUsername();
        if (userId == null) throw new BizException(40100, "UNAUTHENTICATED");

        task.setAssigneeId(userId);
        task.setAssigneeName(userName);
        task.setClaimedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        String workflowId = "process-" + task.getProcessInstanceId();
        ProcessWorkflow workflow = workflowClient.newWorkflowStub(ProcessWorkflow.class, workflowId);
        workflow.approveTask(taskId, request.getAction(), userId, userName, request.getComment());

        log.info("Task approved: taskId={}, action={}, operator={}", taskId, request.getAction(), userName);
        return toResponse(task);
    }

    /**
     * 驳回（退回指定节点）
     */
    @Transactional
    public TaskResponse reject(String taskId, RejectTaskRequest request) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new BizException(40400, "TASK_NOT_FOUND");
        if (!"PENDING".equals(task.getStatus())) throw new BizException(40000, "TASK_ALREADY_PROCESSED");

        String userId = SecurityContextHolder.getUserId();
        String userName = SecurityContextHolder.getUsername();
        if (userId == null) throw new BizException(40100, "UNAUTHENTICATED");

        task.setAssigneeId(userId);
        task.setAssigneeName(userName);
        task.setClaimedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        String workflowId = "process-" + task.getProcessInstanceId();
        ProcessWorkflow workflow = workflowClient.newWorkflowStub(ProcessWorkflow.class, workflowId);
        workflow.rejectToNode(taskId, request.getTargetNodeId(), request.getComment());

        log.info("Task rejected: taskId={}, targetNode={}, operator={}", taskId, request.getTargetNodeId(), userName);
        return toResponse(task);
    }

    /**
     * 转办
     */
    @Transactional
    public TaskResponse transfer(String taskId, TransferTaskRequest request) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new BizException(40400, "TASK_NOT_FOUND");
        if (!"PENDING".equals(task.getStatus())) throw new BizException(40000, "TASK_ALREADY_PROCESSED");

        String userId = SecurityContextHolder.getUserId();
        String userName = SecurityContextHolder.getUsername();
        if (userId == null) throw new BizException(40100, "UNAUTHENTICATED");

        processActivity.transferTask(taskId, request.getNewAssigneeId(), request.getNewAssigneeName());
        log.info("Task transferred: taskId={}, from={}, to={}/{}", taskId, userName,
                request.getNewAssigneeId(), request.getNewAssigneeName());

        return toResponse(taskMapper.selectById(taskId));
    }

    /**
     * 加签
     */
    @Transactional
    public TaskResponse addSign(String taskId, AddSignRequest request) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new BizException(40400, "TASK_NOT_FOUND");
        if (!"PENDING".equals(task.getStatus())) throw new BizException(40000, "TASK_ALREADY_PROCESSED");

        processActivity.addSignTask(task.getProcessInstanceId(), task.getNodeId(),
                task.getNodeName(), request.getAssigneeId(), request.getAssigneeName());

        log.info("AddSign: taskId={}, assignee={}/{}", taskId, request.getAssigneeId(), request.getAssigneeName());
        return toResponse(task);
    }

    /**
     * 获取可驳回的节点列表（已走过的节点）
     */
    public java.util.List<String> getRejectTargets(String processInstanceId) {
        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .eq(Task::getProcessInstanceId, processInstanceId)
                        .in(Task::getStatus, "APPROVED", "REJECTED"))
                .stream()
                .map(Task::getNodeId)
                .distinct()
                .toList();
    }

    private TaskResponse toResponse(Task task) {
        TaskResponse resp = new TaskResponse();
        resp.setId(task.getId());
        resp.setProcessInstanceId(task.getProcessInstanceId());
        resp.setProcessKey(task.getProcessKey());
        resp.setProcessName(task.getProcessName());
        resp.setNodeId(task.getNodeId());
        resp.setNodeName(task.getNodeName());
        resp.setNodeType(task.getNodeType());
        resp.setTitle(task.getTitle());
        resp.setStatus(task.getStatus());
        resp.setAssigneeId(task.getAssigneeId());
        resp.setAssigneeName(task.getAssigneeName());
        resp.setAssigneeType(task.getAssigneeType());
        resp.setComment(task.getComment());
        resp.setClaimedAt(task.getClaimedAt());
        resp.setCompletedAt(task.getCompletedAt());
        resp.setCreatedAt(task.getCreatedAt());
        return resp;
    }
}
