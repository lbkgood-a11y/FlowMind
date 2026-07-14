package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProcessDefinitionValidator {

    private static final Set<String> SUPPORTED_NODE_TYPES = Set.of(
            "START", "APPROVAL", "COUNTERSIGN", "END");
    private static final Set<String> SUPPORTED_ASSIGNMENT_TYPES = Set.of(
            "ROLE", "USER", "DEPT");

    private final ObjectMapper objectMapper;
    private final RestrictedConditionEvaluator conditionEvaluator;
    private final BusinessClosurePolicyValidator businessClosurePolicyValidator;

    public ProcessPackageDefinition validate(String processJson) {
        ProcessPackageDefinition definition = readDefinition(processJson);
        List<ProcessPackageDefinition.NodeSchema> nodes = definition.getFlow().getNodes();
        Map<String, ProcessPackageDefinition.NodeSchema> nodeById = new HashMap<>();
        int startCount = 0;
        int endCount = 0;

        for (ProcessPackageDefinition.NodeSchema node : nodes) {
            if (node == null || !StringUtils.hasText(node.getId())
                    || !StringUtils.hasText(node.getType())) {
                throw new BizException(40000, "PROCESS_NODE_ID_OR_TYPE_REQUIRED");
            }
            String nodeId = node.getId().trim();
            String nodeType = node.getType().trim().toUpperCase(Locale.ROOT);
            node.setId(nodeId);
            node.setType(nodeType);
            if (nodeById.putIfAbsent(nodeId, node) != null) {
                throw new BizException(40000, "DUPLICATE_PROCESS_NODE_ID");
            }
            if (!SUPPORTED_NODE_TYPES.contains(nodeType)) {
                throw new BizException(40000, "UNSUPPORTED_PROCESS_NODE_TYPE");
            }
            if ("START".equals(nodeType)) {
                startCount++;
            } else if ("END".equals(nodeType)) {
                endCount++;
            } else {
                validateParticipant(node);
            }
            validateConditions(node);
        }

        if (startCount != 1) {
            throw new BizException(40000, "PROCESS_REQUIRES_EXACTLY_ONE_START");
        }
        if (endCount < 1) {
            throw new BizException(40000, "PROCESS_REQUIRES_END");
        }

        Map<String, List<String>> edges = buildEdges(nodes, nodeById);
        String startNodeId = nodes.stream()
                .filter(node -> "START".equals(node.getType()))
                .findFirst()
                .orElseThrow()
                .getId();
        Set<String> reachable = reachableFrom(startNodeId, edges);
        if (reachable.size() != nodes.size()) {
            throw new BizException(40000, "PROCESS_CONTAINS_UNREACHABLE_NODES");
        }

        Set<String> canReachEnd = nodesThatCanReachEnd(nodes, edges);
        if (canReachEnd.size() != nodes.size()) {
            throw new BizException(40000, "PROCESS_CONTAINS_DEAD_END_PATH");
        }
        businessClosurePolicyValidator.validate(definition);
        return definition;
    }

    private ProcessPackageDefinition readDefinition(String processJson) {
        try {
            ProcessPackageDefinition definition = objectMapper.readValue(
                    processJson, ProcessPackageDefinition.class);
            if (definition.getFlow() == null
                    || definition.getFlow().getNodes() == null
                    || definition.getFlow().getNodes().isEmpty()) {
                throw new BizException(40000, "PROCESS_FLOW_REQUIRED");
            }
            return definition;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(40000, "INVALID_PROCESS_JSON");
        }
    }

    private void validateParticipant(ProcessPackageDefinition.NodeSchema node) {
        ProcessPackageDefinition.Assignment assignment = node.getAssignment();
        if (assignment == null || !StringUtils.hasText(assignment.getType())) {
            throw new BizException(40000, "PROCESS_PARTICIPANT_REQUIRED");
        }
        String assignmentType = assignment.getType().trim().toUpperCase(Locale.ROOT);
        assignment.setType(assignmentType);
        if (!SUPPORTED_ASSIGNMENT_TYPES.contains(assignmentType)) {
            throw new BizException(40000, "UNSUPPORTED_PARTICIPANT_TYPE");
        }
        String reference = switch (assignmentType) {
            case "ROLE" -> assignment.getRoleCode();
            case "USER" -> assignment.getUserId();
            case "DEPT" -> assignment.getDeptCode();
            default -> null;
        };
        if (!StringUtils.hasText(reference)) {
            throw new BizException(40000, "PARTICIPANT_REFERENCE_REQUIRED");
        }
        if ("COUNTERSIGN".equals(node.getType())
                && !"ALL".equals(node.getStrategy())
                && !"ANY".equals(node.getStrategy())) {
            throw new BizException(40000, "INVALID_COUNTERSIGN_STRATEGY");
        }
    }

    private void validateConditions(ProcessPackageDefinition.NodeSchema node) {
        List<ProcessPackageDefinition.NextCondition> next = node.getNext();
        if (next == null || next.isEmpty()) {
            return;
        }
        if ("END".equals(node.getType())) {
            throw new BizException(40000, "END_NODE_CANNOT_HAVE_OUTGOING_EDGE");
        }

        int defaultCount = 0;
        for (int index = 0; index < next.size(); index++) {
            ProcessPackageDefinition.NextCondition condition = next.get(index);
            if (condition == null || !StringUtils.hasText(condition.getCondition())
                    || !StringUtils.hasText(condition.getTarget())) {
                throw new BizException(40000, "PROCESS_EDGE_CONDITION_OR_TARGET_REQUIRED");
            }
            String expression = condition.getCondition().trim();
            if ("true".equals(expression)) {
                defaultCount++;
                if (index != next.size() - 1) {
                    throw new BizException(40000, "DEFAULT_CONDITION_MUST_BE_LAST");
                }
            } else {
                conditionEvaluator.validate(expression);
            }
        }
        if (defaultCount != 1) {
            throw new BizException(40000, "PROCESS_REQUIRES_ONE_DEFAULT_CONDITION");
        }
    }

    private Map<String, List<String>> buildEdges(
            List<ProcessPackageDefinition.NodeSchema> nodes,
            Map<String, ProcessPackageDefinition.NodeSchema> nodeById) {
        Map<String, List<String>> edges = new HashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            ProcessPackageDefinition.NodeSchema node = nodes.get(index);
            List<String> targets = new ArrayList<>();
            if (node.getNext() != null && !node.getNext().isEmpty()) {
                for (ProcessPackageDefinition.NextCondition condition : node.getNext()) {
                    String target = condition.getTarget().trim();
                    if (!nodeById.containsKey(target)) {
                        throw new BizException(40000, "PROCESS_EDGE_TARGET_NOT_FOUND");
                    }
                    targets.add(target);
                }
            } else if (!"END".equals(node.getType()) && index + 1 < nodes.size()) {
                targets.add(nodes.get(index + 1).getId());
            }
            edges.put(node.getId(), targets);
        }
        return edges;
    }

    private Set<String> reachableFrom(String startNodeId, Map<String, List<String>> edges) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        while (!queue.isEmpty()) {
            String nodeId = queue.removeFirst();
            if (visited.add(nodeId)) {
                queue.addAll(edges.getOrDefault(nodeId, List.of()));
            }
        }
        return visited;
    }

    private Set<String> nodesThatCanReachEnd(
            List<ProcessPackageDefinition.NodeSchema> nodes,
            Map<String, List<String>> edges) {
        Map<String, List<String>> reverseEdges = new HashMap<>();
        edges.forEach((source, targets) -> targets.forEach(target ->
                reverseEdges.computeIfAbsent(target, ignored -> new ArrayList<>()).add(source)));

        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        nodes.stream()
                .filter(node -> "END".equals(node.getType()))
                .map(ProcessPackageDefinition.NodeSchema::getId)
                .forEach(queue::add);
        while (!queue.isEmpty()) {
            String nodeId = queue.removeFirst();
            if (visited.add(nodeId)) {
                queue.addAll(reverseEdges.getOrDefault(nodeId, List.of()));
            }
        }
        return visited;
    }
}
