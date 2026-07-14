package com.triobase.service.workflow.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.workflow.dto.AddSignTaskCommand;
import com.triobase.service.workflow.dto.ConditionEvaluationResult;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.dto.RejectTaskCommand;
import com.triobase.service.workflow.dto.TaskActionCommand;
import com.triobase.service.workflow.dto.TransferTaskCommand;
import com.triobase.service.workflow.service.RestrictedConditionEvaluator;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessWorkflowTest {

    private static final String TASK_QUEUE = "service-workflow-engine";

    private TestWorkflowEnvironment environment;
    private FakeProcessActivity activity;
    private int workflowSequence;

    @BeforeEach
    void setUp() {
        environment = TestWorkflowEnvironment.newInstance();
        activity = new FakeProcessActivity();
        Worker worker = environment.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(ProcessWorkflowImpl.class);
        worker.registerActivitiesImplementations(activity);
        environment.start();
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @Test
    void lowValueExpenseCompletesAfterDepartmentApproval() throws Exception {
        ProcessWorkflow workflow = start(expenseDefinition(), "{\"amount\":3000}");

        await(() -> activity.createdTasks.contains("task-dept-1"));
        workflow.approveTask(approval("op-dept-low-value", "task-dept-1"));
        result(workflow);

        assertFalse(activity.createdTasks.contains("task-finance-1"));
        assertFalse(activity.enteredNodes.contains("finance:1"));
        assertTrue(activity.processCompleted);
    }

    @Test
    void highValueExpenseBranchesToFinanceAndDuplicateSignalIsIgnored() throws Exception {
        ProcessPackageDefinition definition = expenseDefinition();
        ProcessWorkflow workflow = start(definition, "{\"amount\":8000}");

        await(() -> activity.createdTasks.contains("task-dept-1"));
        TaskActionCommand departmentApproval = approval("op-dept", "task-dept-1");
        workflow.approveTask(departmentApproval);
        workflow.approveTask(departmentApproval);

        await(() -> activity.createdTasks.contains("task-finance-1"));
        workflow.approveTask(approval("op-finance", "task-finance-1"));
        result(workflow);

        assertEquals(1, activity.completionCount("task-dept-1"));
        assertTrue(activity.enteredNodes.contains("finance:1"));
        assertTrue(activity.processCompleted);

        WorkflowExecutionHistory history = environment.getWorkflowClient()
                .fetchHistory(workflowId());
        WorkflowReplayer.replayWorkflowExecution(history, ProcessWorkflowImpl.class);
    }

    @Test
    void transferAndParallelAddSignBothRemainRequired() throws Exception {
        ProcessWorkflow workflow = start(singleApprovalDefinition(), "{}");
        await(() -> activity.createdTasks.contains("task-approval-1"));

        AddSignTaskCommand addSign = new AddSignTaskCommand();
        addSign.setOperationId("op-add");
        addSign.setSourceTaskId("task-approval-1");
        addSign.setAddedTaskId("task-added");
        workflow.addSignTask(addSign);

        TransferTaskCommand transfer = new TransferTaskCommand();
        transfer.setOperationId("op-transfer");
        transfer.setSourceTaskId("task-approval-1");
        transfer.setTargetTaskId("task-transferred");
        workflow.transferTask(transfer);

        CompletableFuture<Void> result = WorkflowStub.fromTyped(workflow).getResultAsync(Void.class);
        workflow.approveTask(approval("op-new-owner", "task-transferred"));
        Thread.sleep(100);
        assertFalse(result.isDone());

        workflow.approveTask(approval("op-added-owner", "task-added"));
        result.get(5, TimeUnit.SECONDS);
        assertTrue(activity.processCompleted);
    }

    @Test
    void rejectionCanTerminateOrReturnWithFreshParticipantSnapshot() throws Exception {
        ProcessWorkflow terminated = start(singleApprovalDefinition(), "{}");
        await(() -> activity.createdTasks.contains("task-approval-1"));
        terminated.rejectTask(rejection("op-reject", "task-approval-1", null));
        result(terminated);
        assertTrue(activity.processTerminated);

        resetActivityState();
        ProcessWorkflow returned = start(twoApprovalDefinition(), "{}");
        await(() -> activity.createdTasks.contains("task-first-1"));
        returned.approveTask(approval("op-first", "task-first-1"));
        await(() -> activity.createdTasks.contains("task-second-1"));
        returned.rejectTask(rejection("op-return", "task-second-1", "first"));
        await(() -> activity.createdTasks.contains("task-first-2"));

        returned.approveTask(approval("op-first-again", "task-first-2"));
        await(() -> activity.createdTasks.contains("task-second-2"));
        returned.approveTask(approval("op-second-again", "task-second-2"));
        result(returned);

        assertTrue(activity.resolutionVersions.contains("first:visit-1"));
        assertTrue(activity.resolutionVersions.contains("first:visit-2"));
    }

    @Test
    void countersignAllAndAnyUseDeterministicVoteSets() throws Exception {
        ProcessWorkflow all = start(countersignDefinition("ALL"), "{}");
        await(() -> activity.createdTasks.contains("counter-joint-2-1"));
        CompletableFuture<Void> allResult = WorkflowStub.fromTyped(all).getResultAsync(Void.class);
        all.approveTask(approval("op-all-1", "counter-joint-1-1"));
        Thread.sleep(100);
        assertFalse(allResult.isDone());
        all.approveTask(approval("op-all-2", "counter-joint-2-1"));
        allResult.get(5, TimeUnit.SECONDS);

        resetActivityState();
        ProcessWorkflow any = start(countersignDefinition("ANY"), "{}");
        await(() -> activity.createdTasks.contains("counter-joint-2-1"));
        any.approveTask(approval("op-any", "counter-joint-1-1"));
        result(any);
        assertEquals(1, activity.countersignCancellationCount);
    }

    @Test
    void waitingWorkflowContinuesAfterWorkerPollingRestarts() throws Exception {
        ProcessWorkflow workflow = start(singleApprovalDefinition(), "{}");
        await(() -> activity.createdTasks.contains("task-approval-1"));

        environment.getWorkerFactory().suspendPolling();
        CompletableFuture<Void> result = WorkflowStub.fromTyped(workflow).getResultAsync(Void.class);
        workflow.approveTask(approval("op-after-restart", "task-approval-1"));
        Thread.sleep(100);
        assertFalse(result.isDone());

        environment.getWorkerFactory().resumePolling();
        result.get(5, TimeUnit.SECONDS);
        assertTrue(activity.processCompleted);
    }

    private ProcessWorkflow start(ProcessPackageDefinition definition, String formDataJson) {
        workflowSequence++;
        String workflowId = workflowId();
        ProcessWorkflow workflow = environment.getWorkflowClient().newWorkflowStub(
                ProcessWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TASK_QUEUE)
                        .build());
        WorkflowClient.start(
                workflow::startProcess,
                definition,
                "instance-" + workflowSequence,
                "starter",
                "Starter",
                formDataJson);
        return workflow;
    }

    private String workflowId() {
        return "workflow-test-" + workflowSequence;
    }

    private void result(ProcessWorkflow workflow) throws Exception {
        WorkflowStub.fromTyped(workflow).getResult(5, TimeUnit.SECONDS, Void.class);
    }

    private TaskActionCommand approval(String operationId, String taskId) {
        TaskActionCommand command = new TaskActionCommand();
        command.setOperationId(operationId);
        command.setTaskId(taskId);
        command.setAction("APPROVE");
        command.setUserId("user");
        command.setUserName("User");
        return command;
    }

    private RejectTaskCommand rejection(String operationId, String taskId, String targetNodeId) {
        RejectTaskCommand command = new RejectTaskCommand();
        command.setOperationId(operationId);
        command.setTaskId(taskId);
        command.setUserId("user");
        command.setUserName("User");
        command.setTargetNodeId(targetNodeId);
        command.setComment("Rejected");
        return command;
    }

    private ProcessPackageDefinition expenseDefinition() {
        ProcessPackageDefinition definition = definition();
        definition.getFlow().setNodes(List.of(
                node("start", "START", null, next("true", "dept")),
                node("dept", "APPROVAL", assignment(),
                        next("amount > 5000", "finance"), next("true", "end")),
                node("finance", "APPROVAL", assignment(), next("true", "end")),
                node("end", "END", null)));
        return definition;
    }

    private ProcessPackageDefinition singleApprovalDefinition() {
        ProcessPackageDefinition definition = definition();
        definition.getFlow().setNodes(List.of(
                node("start", "START", null),
                node("approval", "APPROVAL", assignment()),
                node("end", "END", null)));
        return definition;
    }

    private ProcessPackageDefinition twoApprovalDefinition() {
        ProcessPackageDefinition definition = definition();
        definition.getFlow().setNodes(List.of(
                node("start", "START", null),
                node("first", "APPROVAL", assignment()),
                node("second", "APPROVAL", assignment()),
                node("end", "END", null)));
        return definition;
    }

    private ProcessPackageDefinition countersignDefinition(String strategy) {
        ProcessPackageDefinition definition = definition();
        ProcessPackageDefinition.NodeSchema joint = node("joint", "COUNTERSIGN", assignment());
        joint.setStrategy(strategy);
        definition.getFlow().setNodes(List.of(
                node("start", "START", null), joint, node("end", "END", null)));
        return definition;
    }

    private ProcessPackageDefinition definition() {
        ProcessPackageDefinition definition = new ProcessPackageDefinition();
        ProcessPackageDefinition.FlowSchema flow = new ProcessPackageDefinition.FlowSchema();
        definition.setFlow(flow);
        return definition;
    }

    private ProcessPackageDefinition.NodeSchema node(
            String id,
            String type,
            ProcessPackageDefinition.Assignment assignment,
            ProcessPackageDefinition.NextCondition... next) {
        ProcessPackageDefinition.NodeSchema node = new ProcessPackageDefinition.NodeSchema();
        node.setId(id);
        node.setName(id);
        node.setType(type);
        node.setAssignment(assignment);
        node.setNext(next.length > 0 ? List.of(next) : null);
        return node;
    }

    private ProcessPackageDefinition.Assignment assignment() {
        ProcessPackageDefinition.Assignment assignment = new ProcessPackageDefinition.Assignment();
        assignment.setType("USER");
        assignment.setUserId("user");
        return assignment;
    }

    private ProcessPackageDefinition.NextCondition next(String condition, String target) {
        ProcessPackageDefinition.NextCondition next = new ProcessPackageDefinition.NextCondition();
        next.setCondition(condition);
        next.setTarget(target);
        return next;
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), environment.getDiagnostics());
    }

    private void resetActivityState() {
        activity.reset();
    }

    static final class FakeProcessActivity implements ProcessActivity {

        private final RestrictedConditionEvaluator evaluator =
                new RestrictedConditionEvaluator(new ObjectMapper());
        private final List<String> createdTasks = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final List<String> enteredNodes = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final List<String> resolutionVersions = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final Map<String, Integer> completions = new java.util.concurrent.ConcurrentHashMap<>();
        private volatile boolean processCompleted;
        private volatile boolean processTerminated;
        private volatile int countersignCancellationCount;

        @Override
        public String resolveAssignee(ProcessPackageDefinition.Assignment assignment,
                                      String instanceId,
                                      String nodeId,
                                      String participantVersion) {
            resolutionVersions.add(nodeId + ":" + participantVersion);
            return "{\"assignmentType\":\"USER\",\"assignmentRef\":\"user\","
                    + "\"participantVersion\":\"" + participantVersion + "\","
                    + "\"users\":[{\"userId\":\"user\",\"username\":\"User\"}]}";
        }

        @Override
        public String createTask(String instanceId, String nodeId, String nodeName,
                                 String nodeType, int visitNo, String assigneeJson) {
            String taskId = "task-" + nodeId + "-" + visitNo;
            createdTasks.add(taskId);
            return taskId;
        }

        @Override
        public void completeTask(String taskId, String action, String comment) {
            completions.merge(taskId, 1, Integer::sum);
        }

        @Override
        public ConditionEvaluationResult evaluateCondition(String expression, String formDataJson) {
            return evaluator.evaluate(expression, formDataJson);
        }

        @Override
        public List<String> createCountersignTasks(String instanceId, String nodeId,
                                                   String nodeName, String strategy,
                                                   int visitNo, String assigneeListJson) {
            List<String> tasks = List.of(
                    "counter-" + nodeId + "-1-" + visitNo,
                    "counter-" + nodeId + "-2-" + visitNo);
            createdTasks.addAll(tasks);
            return tasks;
        }

        @Override
        public int getCountersignTaskCount(String instanceId, String nodeId) {
            return 2;
        }

        @Override
        public void completeCountersignTask(String taskId, String status, String comment) {
            completions.merge(taskId, 1, Integer::sum);
        }

        @Override
        public void cancelRemainingCountersignTasks(String instanceId, String nodeId) {
            countersignCancellationCount++;
        }

        @Override
        public void rejectToNode(String instanceId, String currentNodeId,
                                 String targetNodeId, String comment) {
        }

        @Override
        public void transferTask(String taskId, String newAssigneeId, String newAssigneeName) {
        }

        @Override
        public void addSignTask(String instanceId, String nodeId, String nodeName,
                                String assigneeId, String assigneeName) {
        }

        @Override
        public void recordNodeEnter(String instanceId, String nodeId, String nodeName,
                                    String nodeType, String prevNodeId, int visitNo) {
            enteredNodes.add(nodeId + ":" + visitNo);
        }

        @Override
        public void recordNodeExit(String instanceId, String nodeId, String resultJson) {
        }

        @Override
        public void failNode(String instanceId, String nodeId, String reason) {
            throw new IllegalStateException(reason);
        }

        @Override
        public void completeProcess(String instanceId) {
            processCompleted = true;
        }

        @Override
        public void terminateProcess(String instanceId, String status, String reason) {
            processTerminated = true;
        }

        int completionCount(String taskId) {
            return completions.getOrDefault(taskId, 0);
        }

        void reset() {
            createdTasks.clear();
            enteredNodes.clear();
            resolutionVersions.clear();
            completions.clear();
            processCompleted = false;
            processTerminated = false;
            countersignCancellationCount = 0;
        }
    }
}
