package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.OrchestrationStepType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OrchestrationDefinitionValidator {

    public static final String SCHEMA_VERSION = "1";
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of("schemaVersion", "start", "steps");
    private static final Set<String> STEP_FIELDS = Set.of(
            "key", "type", "next", "connectorVersionId", "mappingVersionId",
            "inputPointer", "outputPointer", "branches", "defaultNext", "children",
            "durationSeconds", "signalName", "timeoutSeconds", "loopTo", "maxIterations", "compensationStep",
            "retryPreset", "failurePolicy");
    private static final Set<String> BRANCH_FIELDS = Set.of("pointer", "equals", "exists", "next");
    private static final Set<String> RETRY_PRESETS = Set.of("STANDARD", "IDEMPOTENT", "NONE");
    private static final Set<String> FAILURE_POLICIES = Set.of("FAIL", "COMPENSATE", "CONTINUE");
    private static final Set<String> FORBIDDEN_FIELD_PARTS = Set.of(
            "script", "expression", "class", "url", "secret", "credential", "authorization");

    private final ObjectMapper objectMapper;

    public OrchestrationDefinitionValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidationResult validate(String schemaVersion, JsonNode definition) {
        List<String> errors = new ArrayList<>();
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            errors.add("UNSUPPORTED_SCHEMA_VERSION:" + schemaVersion);
        }
        if (definition == null || !definition.isObject()) {
            return new ValidationResult(false, List.of("DEFINITION_MUST_BE_OBJECT"));
        }
        rejectUnknownFields(definition, TOP_LEVEL_FIELDS, "definition", errors);
        if (!schemaVersion.equals(definition.path("schemaVersion").asText())) {
            errors.add("SCHEMA_VERSION_MISMATCH");
        }
        String start = definition.path("start").asText();
        ArrayNode stepsNode = definition.path("steps").isArray()
                ? (ArrayNode) definition.path("steps") : null;
        if (!StringUtils.hasText(start)) {
            errors.add("START_REQUIRED");
        }
        if (stepsNode == null || stepsNode.isEmpty()) {
            errors.add("STEPS_REQUIRED");
            return new ValidationResult(false, List.copyOf(errors));
        }

        Map<String, JsonNode> steps = new HashMap<>();
        for (JsonNode step : stepsNode) {
            validateStepShape(step, steps, errors);
        }
        if (!steps.containsKey(start)) {
            errors.add("START_STEP_NOT_FOUND:" + start);
        }
        validateReferences(steps, errors);
        validateReachability(start, steps, errors);
        validateCycles(start, steps, errors);
        validateTerminalPaths(start, steps, errors);
        scanForbiddenFields(definition, "definition", errors);
        return new ValidationResult(errors.isEmpty(), List.copyOf(new LinkedHashSet<>(errors)));
    }

    public JsonNode validationJson(ValidationResult result) {
        ObjectNode json = objectMapper.createObjectNode().put("valid", result.valid());
        ArrayNode errors = json.putArray("errors");
        result.errors().forEach(errors::add);
        return json;
    }

    public void requireValid(String schemaVersion, JsonNode definition) {
        ValidationResult result = validate(schemaVersion, definition);
        if (!result.valid()) {
            throw new BizException(42250, "OPENAPI_ORCHESTRATION_INVALID:" + String.join(",", result.errors()));
        }
    }

    private void validateStepShape(JsonNode step, Map<String, JsonNode> steps, List<String> errors) {
        if (!step.isObject()) {
            errors.add("STEP_MUST_BE_OBJECT");
            return;
        }
        rejectUnknownFields(step, STEP_FIELDS, "step", errors);
        String key = step.path("key").asText();
        if (!StringUtils.hasText(key) || !key.matches("[A-Za-z][A-Za-z0-9_-]{0,127}")) {
            errors.add("INVALID_STEP_KEY:" + key);
            return;
        }
        if (steps.putIfAbsent(key, step) != null) {
            errors.add("DUPLICATE_STEP_KEY:" + key);
        }
        OrchestrationStepType type = type(step, errors);
        if (type == null) {
            return;
        }
        if (step.has("inputPointer")) {
            validatePointer(step.path("inputPointer").asText(), key, errors);
        }
        if (step.has("outputPointer")) {
            validatePointer(step.path("outputPointer").asText(), key, errors);
        }
        if (step.has("retryPreset") && !RETRY_PRESETS.contains(step.path("retryPreset").asText())) {
            errors.add("INVALID_RETRY_PRESET:" + key);
        }
        if (step.has("failurePolicy") && !FAILURE_POLICIES.contains(step.path("failurePolicy").asText())) {
            errors.add("INVALID_FAILURE_POLICY:" + key);
        }
        switch (type) {
            case INVOKE -> requireText(step, "connectorVersionId", key, errors);
            case TRANSFORM -> requireText(step, "mappingVersionId", key, errors);
            case BRANCH -> validateBranches(step, key, errors);
            case PARALLEL -> {
                if (!step.path("children").isArray() || step.path("children").isEmpty()) {
                    errors.add("PARALLEL_CHILDREN_REQUIRED:" + key);
                }
            }
            case WAIT -> validateWait(step, key, errors);
            case COMPENSATE -> {
                if (!StringUtils.hasText(step.path("connectorVersionId").asText())
                        && !StringUtils.hasText(step.path("mappingVersionId").asText())) {
                    errors.add("COMPENSATION_ACTION_REQUIRED:" + key);
                }
                if (step.has("next")) {
                    errors.add("COMPENSATION_STEP_MUST_NOT_HAVE_NEXT:" + key);
                }
            }
            case END -> {
                if (step.has("next") || step.has("loopTo")) {
                    errors.add("END_STEP_MUST_BE_TERMINAL:" + key);
                }
            }
        }
    }

    private void validateBranches(JsonNode step, String key, List<String> errors) {
        JsonNode branches = step.path("branches");
        if (!branches.isArray() || branches.isEmpty()) {
            errors.add("BRANCH_CASES_REQUIRED:" + key);
            return;
        }
        for (JsonNode branch : branches) {
            if (!branch.isObject()) {
                errors.add("BRANCH_CASE_MUST_BE_OBJECT:" + key);
                continue;
            }
            rejectUnknownFields(branch, BRANCH_FIELDS, "branch:" + key, errors);
            validatePointer(branch.path("pointer").asText(), key, errors);
            if (!branch.has("equals") && !branch.has("exists")) {
                errors.add("BRANCH_CONDITION_REQUIRED:" + key);
            }
            requireText(branch, "next", key, errors);
        }
        requireText(step, "defaultNext", key, errors);
    }

    private void validateWait(JsonNode step, String key, List<String> errors) {
        boolean signalWait = StringUtils.hasText(step.path("signalName").asText());
        long duration = step.path(signalWait ? "timeoutSeconds" : "durationSeconds").asLong(-1);
        if (duration < 0 || duration > 2_592_000) {
            errors.add(signalWait ? "WAIT_SIGNAL_TIMEOUT_INVALID:" + key
                    : "WAIT_DURATION_INVALID:" + key);
        }
        if (step.has("loopTo")) {
            int max = step.path("maxIterations").asInt(0);
            if (max < 1 || max > 100) {
                errors.add("WAIT_LOOP_BOUND_INVALID:" + key);
            }
        } else if (step.has("maxIterations")) {
            errors.add("WAIT_LOOP_TARGET_REQUIRED:" + key);
        }
    }

    private void validateReferences(Map<String, JsonNode> steps, List<String> errors) {
        Set<String> compensationTargets = new HashSet<>();
        for (Map.Entry<String, JsonNode> entry : steps.entrySet()) {
            String key = entry.getKey();
            JsonNode step = entry.getValue();
            for (String target : outgoing(step, true)) {
                if (!steps.containsKey(target)) {
                    errors.add("MISSING_STEP_REFERENCE:" + key + "->" + target);
                }
            }
            if (safeType(step) == OrchestrationStepType.PARALLEL) {
                step.path("children").forEach(childKey -> {
                    JsonNode child = steps.get(childKey.asText());
                    OrchestrationStepType childType = child == null ? null : safeType(child);
                    if (child != null && childType != OrchestrationStepType.INVOKE
                            && childType != OrchestrationStepType.TRANSFORM
                            && childType != OrchestrationStepType.WAIT) {
                        errors.add("UNSUPPORTED_PARALLEL_CHILD:" + key + "->" + childKey.asText());
                    }
                    if (child != null && (child.has("next") || child.has("loopTo"))) {
                        errors.add("PARALLEL_CHILD_MUST_BE_ATOMIC:" + childKey.asText());
                    }
                });
            }
            String compensation = step.path("compensationStep").asText();
            if (StringUtils.hasText(compensation)) {
                compensationTargets.add(compensation);
                JsonNode target = steps.get(compensation);
                if (target == null) {
                    errors.add("MISSING_COMPENSATION_REFERENCE:" + key + "->" + compensation);
                } else if (type(target, errors) != OrchestrationStepType.COMPENSATE) {
                    errors.add("INVALID_COMPENSATION_TARGET:" + key + "->" + compensation);
                }
            }
        }
        steps.forEach((key, step) -> {
            if (safeType(step) == OrchestrationStepType.COMPENSATE && !compensationTargets.contains(key)) {
                errors.add("UNREFERENCED_COMPENSATION_STEP:" + key);
            }
        });
    }

    private void validateReachability(String start, Map<String, JsonNode> steps, List<String> errors) {
        if (!steps.containsKey(start)) {
            return;
        }
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!reachable.add(current)) {
                continue;
            }
            JsonNode step = steps.get(current);
            if (step != null) {
                outgoing(step, false).stream().filter(steps::containsKey).forEach(queue::addLast);
            }
        }
        steps.forEach((key, step) -> {
            if (safeType(step) != OrchestrationStepType.COMPENSATE && !reachable.contains(key)) {
                errors.add("UNREACHABLE_STEP:" + key);
            }
        });
    }

    private void validateCycles(String start, Map<String, JsonNode> steps, List<String> errors) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        detectCycle(start, steps, visiting, visited, errors);
    }

    private void detectCycle(String key, Map<String, JsonNode> steps, Set<String> visiting,
                             Set<String> visited, List<String> errors) {
        if (!steps.containsKey(key) || visited.contains(key)) {
            return;
        }
        if (!visiting.add(key)) {
            errors.add("UNSUPPORTED_CYCLE_AT:" + key);
            return;
        }
        JsonNode step = steps.get(key);
        for (String target : outgoing(step, false)) {
            boolean supportedLoopEdge = safeType(step) == OrchestrationStepType.WAIT
                    && target.equals(step.path("loopTo").asText())
                    && step.path("maxIterations").asInt(0) > 0;
            if (visiting.contains(target) && !supportedLoopEdge) {
                errors.add("UNSUPPORTED_CYCLE:" + key + "->" + target);
            } else if (!supportedLoopEdge) {
                detectCycle(target, steps, visiting, visited, errors);
            }
        }
        visiting.remove(key);
        visited.add(key);
    }

    private void validateTerminalPaths(String start, Map<String, JsonNode> steps, List<String> errors) {
        if (!steps.containsKey(start)) {
            return;
        }
        if (!reachesEnd(start, steps, new HashSet<>())) {
            errors.add("NO_TERMINAL_PATH_FROM_START");
        }
        Set<String> parallelChildren = new HashSet<>();
        steps.values().stream().filter(step -> safeType(step) == OrchestrationStepType.PARALLEL)
                .map(step -> step.path("children")).filter(JsonNode::isArray)
                .forEach(children -> children.forEach(child -> parallelChildren.add(child.asText())));
        steps.forEach((key, step) -> {
            OrchestrationStepType type = safeType(step);
            if (type != null && type != OrchestrationStepType.END
                    && type != OrchestrationStepType.COMPENSATE
                    && !parallelChildren.contains(key)
                    && outgoing(step, false).isEmpty()) {
                errors.add("NON_TERMINAL_STEP_WITHOUT_NEXT:" + key);
            }
        });
    }

    private boolean reachesEnd(String key, Map<String, JsonNode> steps, Set<String> path) {
        JsonNode step = steps.get(key);
        if (step == null) {
            return false;
        }
        if (safeType(step) == OrchestrationStepType.END) {
            return true;
        }
        if (!path.add(key)) {
            return false;
        }
        for (String target : outgoing(step, false)) {
            if (target.equals(step.path("loopTo").asText())) {
                continue;
            }
            if (reachesEnd(target, steps, new HashSet<>(path))) {
                return true;
            }
        }
        return false;
    }

    private List<String> outgoing(JsonNode step, boolean includeCompensation) {
        List<String> targets = new ArrayList<>();
        addText(step, "next", targets);
        addText(step, "defaultNext", targets);
        addText(step, "loopTo", targets);
        if (step.path("branches").isArray()) {
            step.path("branches").forEach(branch -> addText(branch, "next", targets));
        }
        if (step.path("children").isArray()) {
            step.path("children").forEach(child -> {
                if (child.isTextual() && StringUtils.hasText(child.asText())) {
                    targets.add(child.asText());
                }
            });
        }
        if (includeCompensation) {
            addText(step, "compensationStep", targets);
        }
        return targets;
    }

    private void rejectUnknownFields(JsonNode node, Set<String> allowed, String path, List<String> errors) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                errors.add("UNKNOWN_FIELD:" + path + "." + field);
            }
        });
    }

    private void scanForbiddenFields(JsonNode node, String path, List<String> errors) {
        if (node == null || node.isValueNode()) {
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                scanForbiddenFields(node.get(index), path + '[' + index + ']', errors);
            }
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String normalized = field.getKey().toLowerCase();
            if (FORBIDDEN_FIELD_PARTS.stream().anyMatch(normalized::contains)) {
                errors.add("FORBIDDEN_CONFIGURATION:" + path + '.' + field.getKey());
            }
            scanForbiddenFields(field.getValue(), path + '.' + field.getKey(), errors);
        }
    }

    private void validatePointer(String pointer, String key, List<String> errors) {
        if (!StringUtils.hasText(pointer) || (!pointer.isEmpty() && !pointer.startsWith("/"))
                || pointer.contains("..") || pointer.contains("*")) {
            errors.add("INVALID_JSON_POINTER:" + key + ':' + pointer);
        }
    }

    private void requireText(JsonNode node, String field, String key, List<String> errors) {
        if (!StringUtils.hasText(node.path(field).asText())) {
            errors.add("FIELD_REQUIRED:" + key + ':' + field);
        }
    }

    private void addText(JsonNode node, String field, List<String> targets) {
        String value = node.path(field).asText();
        if (StringUtils.hasText(value)) {
            targets.add(value);
        }
    }

    private OrchestrationStepType type(JsonNode step, List<String> errors) {
        try {
            return OrchestrationStepType.valueOf(step.path("type").asText());
        } catch (Exception exception) {
            errors.add("UNSUPPORTED_STEP_TYPE:" + step.path("type").asText());
            return null;
        }
    }

    private OrchestrationStepType safeType(JsonNode step) {
        try {
            return OrchestrationStepType.valueOf(step.path("type").asText());
        } catch (Exception ignored) {
            return null;
        }
    }

    public record ValidationResult(boolean valid, List<String> errors) {
    }
}
