package com.triobase.service.openapi.temporal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.temporal.policy.RetryPolicyPresets;
import io.temporal.activity.ActivityOptions;
import io.temporal.failure.CanceledFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WorkflowImpl(taskQueues = "service-openapi")
public class IntegrationOrchestrationWorkflowImpl implements IntegrationOrchestrationWorkflow {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntegrationOrchestrationActivities standardActivities = Workflow.newActivityStub(
            IntegrationOrchestrationActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryPolicyPresets.standard())
                    .build());
    private final IntegrationOrchestrationActivities idempotentActivities = Workflow.newActivityStub(
            IntegrationOrchestrationActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setHeartbeatTimeout(Duration.ofSeconds(15))
                    .setRetryOptions(RetryPolicyPresets.idempotent())
                    .build());

    private final Map<String, JsonNode> receivedSignals = new LinkedHashMap<>();
    private final Map<String, Integer> waitIterations = new HashMap<>();
    private String executionId;
    private String state = "ACCEPTED";
    private String resultJson = "{}";
    private boolean cancelRequested;
    private String cancelReason = "CANCELLED_BY_CALLER";

    @Override
    public String run(String commandJson) {
        long startedAt = Workflow.currentTimeMillis();
        JsonNode loaded = read(standardActivities.loadRelease(commandJson));
        executionId = loaded.path("executionId").asText();
        JsonNode definition = loaded.path("definition");
        JsonNode payload = loaded.path("payload");
        JsonNode context = loaded.path("context");
        Map<String, JsonNode> steps = indexSteps(definition.path("steps"));
        List<String> compensationStack = new ArrayList<>();
        state = "RUNNING";
        persistState(context, null, false, startedAt);

        String current = definition.path("start").asText();
        int transitions = 0;
        try {
            while (current != null && !current.isBlank()) {
                if (++transitions > 10_000) {
                    throw new IllegalStateException("ORCHESTRATION_TRANSITION_LIMIT_EXCEEDED");
                }
                if (cancelRequested) {
                    compensateAll(compensationStack, steps, payload, context);
                    state = "CANCELLED";
                    resultJson = result(payload, cancelReason, false, startedAt);
                    persistState(context, cancelReason, false, startedAt);
                    return resultJson;
                }
                JsonNode step = steps.get(current);
                if (step == null) {
                    throw new IllegalStateException("ORCHESTRATION_STEP_NOT_FOUND:" + current);
                }
                String type = step.path("type").asText();
                if ("END".equals(type)) {
                    state = "SUCCEEDED";
                    resultJson = result(payload, null, false, startedAt);
                    persistState(context, null, false, startedAt);
                    return resultJson;
                }
                StepOutcome outcome;
                try {
                    outcome = executeStep(step, payload, context, steps);
                } catch (RuntimeException failure) {
                    String policy = step.path("failurePolicy").asText("COMPENSATE");
                    if ("CONTINUE".equals(policy)) {
                        current = text(step, "next");
                        continue;
                    }
                    boolean compensationFailed = !compensateAll(
                            compensationStack, steps, payload, context);
                    state = compensationFailed ? "FAILED" : "COMPENSATED";
                    String error = sanitize(failure.getMessage());
                    resultJson = result(payload, error, true, startedAt);
                    persistState(context, error, true, startedAt);
                    return resultJson;
                }
                payload = outcome.payload();
                if (step.hasNonNull("compensationStep")) {
                    compensationStack.add(step.path("compensationStep").asText());
                }
                current = outcome.next();
            }
            throw new IllegalStateException("ORCHESTRATION_TERMINATED_WITHOUT_END");
        } catch (CanceledFailure failure) {
            compensateAll(compensationStack, steps, payload, context);
            state = "CANCELLED";
            resultJson = result(payload, "TEMPORAL_CANCELLATION", false, startedAt);
            persistState(context, "TEMPORAL_CANCELLATION", false, startedAt);
            return resultJson;
        }
    }

    private StepOutcome executeStep(JsonNode step, JsonNode payload, JsonNode context,
                                    Map<String, JsonNode> steps) {
        return switch (step.path("type").asText()) {
            case "INVOKE" -> new StepOutcome(
                    executeActivity(idempotentActivities::invokeConnector, step, payload, context),
                    text(step, "next"));
            case "TRANSFORM" -> new StepOutcome(
                    executeActivity(standardActivities::transform, step, payload, context),
                    text(step, "next"));
            case "BRANCH" -> new StepOutcome(payload, selectBranch(step, payload));
            case "PARALLEL" -> new StepOutcome(executeParallel(step, payload, context, steps),
                    text(step, "next"));
            case "WAIT" -> executeWait(step, payload, context);
            default -> throw new IllegalStateException(
                    "UNSUPPORTED_ORCHESTRATION_STEP:" + step.path("type").asText());
        };
    }

    private JsonNode executeParallel(JsonNode step, JsonNode payload, JsonNode context,
                                     Map<String, JsonNode> steps) {
        List<String> childKeys = new ArrayList<>();
        List<Promise<JsonNode>> promises = new ArrayList<>();
        for (JsonNode childKeyNode : step.path("children")) {
            String childKey = childKeyNode.asText();
            JsonNode child = steps.get(childKey);
            if (child == null) {
                throw new IllegalStateException("PARALLEL_CHILD_NOT_FOUND:" + childKey);
            }
            childKeys.add(childKey);
            promises.add(Async.function(() -> executeAtomic(child, payload.deepCopy(), context)));
        }
        ObjectNode results = objectMapper.createObjectNode();
        for (int index = 0; index < promises.size(); index++) {
            results.set(childKeys.get(index), promises.get(index).get());
        }
        return placeResult(payload, step.path("outputPointer").asText(), results);
    }

    private JsonNode executeAtomic(JsonNode step, JsonNode payload, JsonNode context) {
        return switch (step.path("type").asText()) {
            case "INVOKE" -> executeActivity(idempotentActivities::invokeConnector, step, payload, context);
            case "TRANSFORM" -> executeActivity(standardActivities::transform, step, payload, context);
            case "WAIT" -> executeWait(step, payload, context).payload();
            default -> throw new IllegalStateException(
                    "UNSUPPORTED_PARALLEL_CHILD_TYPE:" + step.path("type").asText());
        };
    }

    private StepOutcome executeWait(JsonNode step, JsonNode payload, JsonNode context) {
        String stepKey = step.path("key").asText();
        standardActivities.persistWait(envelope(step, payload, context, "WAITING").toString());
        JsonNode nextPayload = payload;
        String signalName = step.path("signalName").asText();
        if (!signalName.isBlank()) {
            state = "WAITING_CALLBACK";
            persistState(context, null, false, Workflow.currentTimeMillis());
            long timeout = step.path("timeoutSeconds").asLong(86_400);
            boolean received = Workflow.await(Duration.ofSeconds(timeout),
                    () -> receivedSignals.containsKey(signalName) || cancelRequested);
            if (cancelRequested) {
                return new StepOutcome(payload, text(step, "next"));
            }
            if (!received) {
                throw new IllegalStateException("ORCHESTRATION_SIGNAL_TIMEOUT:" + signalName);
            }
            nextPayload = receivedSignals.remove(signalName);
            state = "RUNNING";
        } else {
            Workflow.sleep(Duration.ofSeconds(step.path("durationSeconds").asLong()));
        }
        standardActivities.persistWait(envelope(step, nextPayload, context, "RESUMED").toString());
        int iteration = waitIterations.merge(stepKey, 1, Integer::sum);
        String loopTo = text(step, "loopTo");
        int maxIterations = step.path("maxIterations").asInt(0);
        String next = loopTo != null && iteration < maxIterations ? loopTo : text(step, "next");
        return new StepOutcome(nextPayload, next);
    }

    private JsonNode executeActivity(ActivityCall call, JsonNode step, JsonNode payload, JsonNode context) {
        JsonNode response = read(call.invoke(envelope(step, payload, context, "EXECUTE").toString()));
        JsonNode activityPayload = response.has("payload") ? response.path("payload") : response;
        return placeResult(payload, step.path("outputPointer").asText(), activityPayload);
    }

    private boolean compensateAll(List<String> compensationStack, Map<String, JsonNode> steps,
                                  JsonNode payload, JsonNode context) {
        if (compensationStack.isEmpty()) {
            return true;
        }
        state = "COMPENSATING";
        boolean succeeded = true;
        for (int index = compensationStack.size() - 1; index >= 0; index--) {
            JsonNode compensation = steps.get(compensationStack.get(index));
            if (compensation == null) {
                succeeded = false;
                continue;
            }
            try {
                idempotentActivities.compensate(
                        envelope(compensation, payload, context, "COMPENSATE").toString());
            } catch (RuntimeException failure) {
                succeeded = false;
            }
        }
        return succeeded;
    }

    private String selectBranch(JsonNode step, JsonNode payload) {
        for (JsonNode branch : step.path("branches")) {
            JsonNode value = payload.at(branch.path("pointer").asText());
            boolean matches = branch.has("equals") && value.equals(branch.path("equals"));
            if (branch.has("exists")) {
                matches = branch.path("exists").asBoolean() == !value.isMissingNode();
            }
            if (matches) {
                return branch.path("next").asText();
            }
        }
        return step.path("defaultNext").asText();
    }

    private ObjectNode envelope(JsonNode step, JsonNode payload, JsonNode context, String phase) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("executionId", executionId);
        command.put("phase", phase);
        command.set("step", step);
        command.set("payload", payload);
        command.set("context", context);
        return command;
    }

    private void persistState(JsonNode context, String error, boolean partialFailure, long startedAt) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("executionId", executionId);
        command.put("state", state);
        command.put("workflowTimeMillis", Workflow.currentTimeMillis());
        command.put("durationMillis", Math.max(0, Workflow.currentTimeMillis() - startedAt));
        command.put("partialFailure", partialFailure);
        if (error != null) {
            command.put("error", error);
        }
        command.set("context", context);
        standardActivities.persistExecution(command.toString());
    }

    private String result(JsonNode payload, String error, boolean partialFailure, long startedAt) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("executionId", executionId);
        result.put("state", state);
        result.put("partialFailure", partialFailure);
        result.put("durationMillis", Math.max(0, Workflow.currentTimeMillis() - startedAt));
        result.set("payload", payload);
        if (error != null) {
            result.put("error", error);
        }
        return result.toString();
    }

    private JsonNode placeResult(JsonNode original, String pointer, JsonNode value) {
        if (pointer == null || pointer.isBlank()) {
            return value;
        }
        ObjectNode root = original != null && original.isObject()
                ? ((ObjectNode) original.deepCopy()) : objectMapper.createObjectNode();
        String[] parts = pointer.substring(1).split("/");
        ObjectNode current = root;
        for (int index = 0; index < parts.length - 1; index++) {
            String part = unescape(parts[index]);
            JsonNode child = current.get(part);
            if (!(child instanceof ObjectNode)) {
                child = current.putObject(part);
            }
            current = (ObjectNode) child;
        }
        current.set(unescape(parts[parts.length - 1]), value);
        return root;
    }

    private Map<String, JsonNode> indexSteps(JsonNode steps) {
        Map<String, JsonNode> indexed = new LinkedHashMap<>();
        if (steps instanceof ArrayNode array) {
            for (JsonNode step : array) {
                indexed.put(step.path("key").asText(), step);
            }
        }
        return indexed;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("ORCHESTRATION_JSON_CONTRACT_INVALID", exception);
        }
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        return value.isBlank() ? null : value;
    }

    private String sanitize(String message) {
        if (message == null) {
            return "ORCHESTRATION_STEP_FAILED";
        }
        String sanitized = message.replaceAll(
                "(?i)(bearer|basic)\\s+[A-Za-z0-9._~+/=-]+", "$1 ***");
        return sanitized.length() > 512 ? sanitized.substring(0, 512) : sanitized;
    }

    private String unescape(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    @Override
    public void receiveSignal(String signalJson) {
        JsonNode signal = read(signalJson);
        String name = signal.path("name").asText();
        if (!name.isBlank() && !receivedSignals.containsKey(name)) {
            receivedSignals.put(name, signal.path("payload"));
        }
    }

    @Override
    public void requestCancel(String reason) {
        cancelRequested = true;
        if (reason != null && !reason.isBlank()) {
            cancelReason = sanitize(reason);
        }
    }

    @Override
    public String status() {
        ObjectNode status = objectMapper.createObjectNode();
        status.put("executionId", executionId == null ? "" : executionId);
        status.put("state", state);
        status.put("cancelRequested", cancelRequested);
        return status.toString();
    }

    @Override
    public String result() {
        return resultJson;
    }

    @FunctionalInterface
    private interface ActivityCall {
        String invoke(String commandJson);
    }

    private record StepOutcome(JsonNode payload, String next) {
    }
}
