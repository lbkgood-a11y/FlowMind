package com.triobase.service.openapi.temporal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.failure.ApplicationFailure;
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

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationOrchestrationWorkflowTest {

    private static final String TASK_QUEUE = "service-openapi";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TestWorkflowEnvironment environment;
    private FakeActivities activities;
    private int sequence;

    @BeforeEach
    void setUp() {
        environment = TestWorkflowEnvironment.newInstance();
        activities = new FakeActivities(objectMapper);
        Worker worker = environment.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(IntegrationOrchestrationWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        environment.start();
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @Test
    void executesBranchParallelAndTimerAndReplaysDeterministically() throws Exception {
        JsonNode definition = objectMapper.readTree("""
                {"schemaVersion":"1","start":"branch","steps":[
                  {"key":"branch","type":"BRANCH","branches":[
                    {"pointer":"/priority","equals":"HIGH","next":"parallel"}
                  ],"defaultNext":"timer"},
                  {"key":"parallel","type":"PARALLEL","children":["map","invoke"],
                   "outputPointer":"/parallel","next":"timer"},
                  {"key":"map","type":"TRANSFORM","mappingVersionId":"m1"},
                  {"key":"invoke","type":"INVOKE","connectorVersionId":"c1"},
                  {"key":"timer","type":"WAIT","durationSeconds":2,"next":"end"},
                  {"key":"end","type":"END"}
                ]}
                """);
        activities.definition = definition;
        String workflowId = nextWorkflowId();
        IntegrationOrchestrationWorkflow workflow = start(
                workflowId, objectMapper.readTree("{\"priority\":\"HIGH\"}"));

        JsonNode result = objectMapper.readTree(
                WorkflowStub.fromTyped(workflow).getResult(10, TimeUnit.SECONDS, String.class));

        assertThat(result.path("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.at("/payload/parallel/map/mappedBy").asText()).isEqualTo("map");
        assertThat(result.at("/payload/parallel/invoke/invokedBy").asText()).isEqualTo("invoke");
        assertThat(activities.waitPhases).containsExactly("WAITING", "RESUMED");
        WorkflowExecutionHistory history = environment.getWorkflowClient().fetchHistory(workflowId);
        WorkflowReplayer.replayWorkflowExecution(history, IntegrationOrchestrationWorkflowImpl.class);
    }

    @Test
    void retriesTransientActivityAndRecordsSuccessfulCompletion() throws Exception {
        activities.definition = objectMapper.readTree("""
                {"schemaVersion":"1","start":"invoke","steps":[
                  {"key":"invoke","type":"INVOKE","connectorVersionId":"c1","next":"end"},
                  {"key":"end","type":"END"}
                ]}
                """);
        activities.transientFailuresRemaining = 2;
        IntegrationOrchestrationWorkflow workflow = start(nextWorkflowId(), objectMapper.createObjectNode());

        JsonNode result = objectMapper.readTree(
                WorkflowStub.fromTyped(workflow).getResult(15, TimeUnit.SECONDS, String.class));

        assertThat(result.path("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(activities.invokeAttempts).isEqualTo(3);
    }

    @Test
    void compensatesCompletedStepsInReverseOrderOnPartialFailure() throws Exception {
        activities.definition = objectMapper.readTree("""
                {"schemaVersion":"1","start":"first","steps":[
                  {"key":"first","type":"INVOKE","connectorVersionId":"c1",
                   "compensationStep":"undo-first","next":"second"},
                  {"key":"second","type":"INVOKE","connectorVersionId":"c2",
                   "compensationStep":"undo-second","next":"end"},
                  {"key":"undo-first","type":"COMPENSATE","connectorVersionId":"u1"},
                  {"key":"undo-second","type":"COMPENSATE","connectorVersionId":"u2"},
                  {"key":"end","type":"END"}
                ]}
                """);
        activities.alwaysFailStep = "second";
        IntegrationOrchestrationWorkflow workflow = start(nextWorkflowId(), objectMapper.createObjectNode());

        JsonNode result = objectMapper.readTree(
                WorkflowStub.fromTyped(workflow).getResult(20, TimeUnit.SECONDS, String.class));

        assertThat(result.path("state").asText()).isEqualTo("COMPENSATED");
        assertThat(result.path("partialFailure").asBoolean()).isTrue();
        assertThat(activities.compensations).containsExactly("undo-first");
    }

    @Test
    void cancellationAndWorkerPollingRestartResumeWaitingWorkflow() throws Exception {
        activities.definition = objectMapper.readTree("""
                {"schemaVersion":"1","start":"wait","steps":[
                  {"key":"wait","type":"WAIT","signalName":"partner-result",
                   "timeoutSeconds":3600,"next":"end"},
                  {"key":"end","type":"END"}
                ]}
                """);
        IntegrationOrchestrationWorkflow workflow = start(nextWorkflowId(), objectMapper.createObjectNode());
        awaitState(workflow, "WAITING_CALLBACK");

        environment.getWorkerFactory().suspendPolling();
        Thread.sleep(500);
        CompletableFuture<String> result = WorkflowStub.fromTyped(workflow).getResultAsync(String.class);
        workflow.requestCancel("operator-request");
        environment.getWorkerFactory().resumePolling();
        JsonNode completed = objectMapper.readTree(result.get(10, TimeUnit.SECONDS));
        assertThat(completed.path("state").asText()).isEqualTo("CANCELLED");
    }

    private IntegrationOrchestrationWorkflow start(String workflowId, JsonNode payload) {
        IntegrationOrchestrationWorkflow workflow = environment.getWorkflowClient().newWorkflowStub(
                IntegrationOrchestrationWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId(workflowId).setTaskQueue(TASK_QUEUE).build());
        ObjectNode command = objectMapper.createObjectNode();
        command.put("executionId", "execution-" + sequence);
        command.put("releaseId", "release-1");
        command.set("payload", payload);
        command.set("context", objectMapper.valueToTree(Map.of(
                "traceId", "trace-1", "tenantId", "tenant-a",
                "applicationClientId", "client-a", "callerId", "client-a",
                "releaseId", "release-1", "idempotencyKey", "idem-1")));
        WorkflowClient.start(workflow::run, command.toString());
        return workflow;
    }

    private String nextWorkflowId() {
        sequence++;
        return "openapi-workflow-test-" + sequence;
    }

    private void awaitState(IntegrationOrchestrationWorkflow workflow, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (expected.equals(objectMapper.readTree(workflow.status()).path("state").asText())) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError(environment.getDiagnostics());
    }

    static final class FakeActivities implements IntegrationOrchestrationActivities {
        private final ObjectMapper objectMapper;
        private JsonNode definition;
        private int transientFailuresRemaining;
        private int invokeAttempts;
        private String alwaysFailStep;
        private final List<String> compensations = new ArrayList<>();
        private final List<String> waitPhases = new ArrayList<>();

        FakeActivities(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public String loadRelease(String commandJson) {
            try {
                ObjectNode loaded = (ObjectNode) objectMapper.readTree(commandJson);
                loaded.set("definition", definition);
                return loaded.toString();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public String transform(String commandJson) {
            ObjectNode payload = objectMapper.createObjectNode().put("mappedBy", stepKey(commandJson));
            return objectMapper.createObjectNode().set("payload", payload).toString();
        }

        @Override
        public String invokeConnector(String commandJson) {
            invokeAttempts++;
            String step = stepKey(commandJson);
            if (step.equals(alwaysFailStep) || transientFailuresRemaining-- > 0) {
                throw ApplicationFailure.newFailure("transient", "TRANSIENT");
            }
            ObjectNode payload = objectMapper.createObjectNode().put("invokedBy", step);
            return objectMapper.createObjectNode().set("payload", payload).toString();
        }

        @Override
        public String persistExecution(String stateCommandJson) {
            return "{\"persisted\":true}";
        }

        @Override
        public String persistWait(String waitCommandJson) {
            try {
                waitPhases.add(objectMapper.readTree(waitCommandJson).path("phase").asText());
                return "{\"persisted\":true}";
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public String compensate(String compensationCommandJson) {
            compensations.add(stepKey(compensationCommandJson));
            return "{\"payload\":{}}";
        }

        private String stepKey(String commandJson) {
            try {
                return objectMapper.readTree(commandJson).path("step").path("key").asText();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
