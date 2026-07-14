package com.triobase.service.workflow.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.dto.internal.ResolvedUserDto;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.dto.ResolvedParticipants;
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
import com.triobase.service.workflow.service.ProcessOutcomeService;
import com.triobase.service.workflow.service.RestrictedConditionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessActivityImplTest {

    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TaskCandidateMapper taskCandidateMapper;
    @Mock
    private NodeRecordMapper nodeRecordMapper;
    @Mock
    private ParticipantResolutionMapper participantResolutionMapper;
    @Mock
    private ParticipantResolver participantResolver;
    @Mock
    private RestrictedConditionEvaluator conditionEvaluator;
    @Mock
    private ProcessOutcomeService processOutcomeService;

    private ObjectMapper objectMapper;
    private ProcessActivityImpl activity;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        activity = new ProcessActivityImpl(
                objectMapper,
                processInstanceMapper,
                taskMapper,
                taskCandidateMapper,
                nodeRecordMapper,
                participantResolutionMapper,
                participantResolver,
                conditionEvaluator,
                processOutcomeService);
    }

    @Test
    void resolutionPersistsImmutableNodeSnapshotAndReusesItOnRetry() {
        ProcessPackageDefinition.Assignment assignment = assignment();
        ResolvedParticipants participants = participants("u1", "u2");
        NodeRecord nodeRecord = nodeRecord();
        when(participantResolver.participantVersion(assignment)).thenReturn("assignment-version");
        when(participantResolver.resolve(assignment)).thenReturn(participants);
        when(participantResolutionMapper.selectOne(any())).thenReturn(null);
        when(nodeRecordMapper.selectOne(any())).thenReturn(nodeRecord);

        String json = activity.resolveAssignee(
                assignment, "instance-1", "approve-1", "visit-1");

        assertTrue(json.contains("u1"));
        assertEquals(json, nodeRecord.getAssigneeSnapshot());
        verify(participantResolutionMapper).insert(any(ParticipantResolution.class));
        verify(nodeRecordMapper).updateById(nodeRecord);

        ParticipantResolution persisted = new ParticipantResolution();
        persisted.setParticipantsJson(json);
        when(participantResolutionMapper.selectOne(any())).thenReturn(persisted);
        activity.resolveAssignee(assignment, "instance-1", "approve-1", "visit-1");

        verify(participantResolver, times(1)).resolve(assignment);
    }

    @Test
    void emptyResolutionFailsNodeAndSuspendsInstanceWithoutTask() {
        ProcessPackageDefinition.Assignment assignment = assignment();
        ResolvedParticipants participants = participants();
        NodeRecord nodeRecord = nodeRecord();
        ProcessInstance instance = processInstance();
        when(participantResolver.participantVersion(assignment)).thenReturn("assignment-version");
        when(participantResolver.resolve(assignment)).thenReturn(participants);
        when(participantResolutionMapper.selectOne(any())).thenReturn(null);
        when(nodeRecordMapper.selectOne(any())).thenReturn(nodeRecord);
        when(processInstanceMapper.selectById("instance-1")).thenReturn(instance);

        assertThrows(ParticipantResolutionException.class,
                () -> activity.resolveAssignee(
                        assignment, "instance-1", "approve-1", "visit-1"));

        assertEquals("FAILED", nodeRecord.getStatus());
        assertEquals("SUSPENDED", instance.getStatus());
        verify(taskMapper, never()).insert(any(Task.class));
    }

    @Test
    void approvalCreatesOneUnassignedTaskAndCandidateRows() throws Exception {
        ProcessInstance instance = processInstance();
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(processInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(taskCandidateMapper.selectCount(any())).thenReturn(0L);

        String taskId = activity.createTask(
                "instance-1", "approve-1", "Finance approval", "APPROVAL",
                1, objectMapper.writeValueAsString(participants("u1", "u2")));

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertEquals(taskId, taskCaptor.getValue().getId());
        assertNull(taskCaptor.getValue().getAssigneeId());
        assertEquals("ROLE", taskCaptor.getValue().getAssigneeType());
        verify(taskCandidateMapper, times(2)).insert(any(TaskCandidate.class));
    }

    @Test
    void countersignCreatesOneAssignedTaskPerResolvedUser() throws Exception {
        when(processInstanceMapper.selectById("instance-1")).thenReturn(processInstance());
        when(taskMapper.selectOne(any())).thenReturn(null);

        activity.createCountersignTasks(
                "instance-1", "approve-1", "Joint approval", "ALL",
                1, objectMapper.writeValueAsString(participants("u1", "u2")));

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper, times(2)).insert(taskCaptor.capture());
        assertEquals(List.of("u1", "u2"),
                taskCaptor.getAllValues().stream().map(Task::getAssigneeId).toList());
    }

    private ProcessPackageDefinition.Assignment assignment() {
        ProcessPackageDefinition.Assignment assignment = new ProcessPackageDefinition.Assignment();
        assignment.setType("ROLE");
        assignment.setRoleCode("FINANCE");
        return assignment;
    }

    private ResolvedParticipants participants(String... userIds) {
        ResolvedParticipants participants = new ResolvedParticipants();
        participants.setAssignmentType("ROLE");
        participants.setAssignmentRef("FINANCE");
        participants.setParticipantVersion("version-1");
        participants.setUsers(java.util.Arrays.stream(userIds)
                .map(id -> new ResolvedUserDto(id, "User " + id))
                .toList());
        return participants;
    }

    private ProcessInstance processInstance() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId("instance-1");
        instance.setProcessKey("expense");
        instance.setProcessName("Expense");
        instance.setStatus("RUNNING");
        return instance;
    }

    private NodeRecord nodeRecord() {
        NodeRecord record = new NodeRecord();
        record.setId("node-record-1");
        record.setProcessInstanceId("instance-1");
        record.setNodeId("approve-1");
        record.setStatus("ACTIVE");
        return record;
    }
}
