package com.triobase.service.workflow.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.internal.ResolvedUserDto;
import com.triobase.service.workflow.dto.AddSignRequest;
import com.triobase.service.workflow.dto.ResolvedParticipants;
import com.triobase.service.workflow.entity.Task;
import com.triobase.service.workflow.entity.TaskOperation;
import com.triobase.service.workflow.mapper.NodeRecordMapper;
import com.triobase.service.workflow.mapper.TaskMapper;
import com.triobase.service.workflow.mapper.TaskOperationMapper;
import com.triobase.service.workflow.workflow.ProcessWorkflow;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceCandidateAuthorizationTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TaskOperationMapper taskOperationMapper;
    @Mock
    private NodeRecordMapper nodeRecordMapper;
    @Mock
    private WorkflowClient workflowClient;
    @Mock
    private ParticipantResolver participantResolver;
    @Mock
    private ProcessWorkflow workflow;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(
                taskMapper,
                taskOperationMapper,
                nodeRecordMapper,
                workflowClient,
                participantResolver);
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "Alice", List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void nonCandidateCannotAddSign() {
        Task task = pendingTask();
        when(taskOperationMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.claimPendingTask("task-1", "user-1", "Alice")).thenReturn(0);
        when(taskMapper.selectById("task-1")).thenReturn(task);

        AddSignRequest request = addSignRequest("op-1");
        BizException exception = assertThrows(BizException.class,
                () -> taskService.addSign("task-1", request));

        assertEquals("TASK_NOT_CANDIDATE", exception.getMessage());
        verify(participantResolver, never()).resolve(any());
    }

    @Test
    void addSignCreatesLinkedTaskAndSendsTypedSignal() {
        Task task = pendingTask();
        when(taskOperationMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.claimPendingTask("task-1", "user-1", "Alice")).thenReturn(1);
        when(taskMapper.selectById("task-1")).thenReturn(task);
        when(taskOperationMapper.insertIfAbsent(any(TaskOperation.class))).thenReturn(1);
        when(workflowClient.newWorkflowStub(ProcessWorkflow.class, "process-instance-1"))
                .thenReturn(workflow);

        ResolvedParticipants participants = new ResolvedParticipants();
        participants.setUsers(List.of(new ResolvedUserDto("user-2", "Bob")));
        when(participantResolver.resolve(any())).thenReturn(participants);

        taskService.addSign("task-1", addSignRequest("op-1"));

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertEquals("task-1", taskCaptor.getValue().getSourceTaskId());
        assertEquals("user-2", taskCaptor.getValue().getAssigneeId());
        verify(workflow).addSignTask(any());
    }

    @Test
    void duplicateOperationReturnsOriginalTaskWithoutSecondSignal() {
        Task task = pendingTask();
        task.setStatus("APPROVED");
        TaskOperation operation = new TaskOperation();
        operation.setOperationId("op-1");
        operation.setSourceTaskId("task-1");
        operation.setAction("APPROVE");
        when(taskOperationMapper.selectOne(any())).thenReturn(operation);
        when(taskMapper.selectById("task-1")).thenReturn(task);

        com.triobase.service.workflow.dto.ApproveTaskRequest request =
                new com.triobase.service.workflow.dto.ApproveTaskRequest();
        request.setOperationId("op-1");
        request.setAction("APPROVE");

        assertEquals("APPROVED", taskService.approve("task-1", request).getStatus());
        verify(workflowClient, never()).newWorkflowStub(any(), any(String.class));
    }

    private AddSignRequest addSignRequest(String operationId) {
        AddSignRequest request = new AddSignRequest();
        request.setOperationId(operationId);
        request.setAssigneeId("user-2");
        request.setAssigneeName("Bob");
        return request;
    }

    private Task pendingTask() {
        Task task = new Task();
        task.setId("task-1");
        task.setProcessInstanceId("instance-1");
        task.setProcessKey("expense");
        task.setProcessName("Expense");
        task.setNodeId("approve-1");
        task.setNodeName("Approval");
        task.setNodeType("APPROVAL");
        task.setNodeVisitNo(1);
        task.setTitle("Expense - Approval");
        task.setStatus("PENDING");
        return task;
    }
}
