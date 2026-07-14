package com.triobase.service.workflow.executor;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProcessExecutorRegistry {

    private final Map<String, BusinessActionExecutor> businessActionExecutors;
    private final Map<String, ClosureEffectExecutor> closureEffectExecutors;
    private final Map<String, AgentFollowUpExecutor> agentFollowUpExecutors;

    public ProcessExecutorRegistry(List<BusinessActionExecutor> businessActionExecutors,
                                   List<ClosureEffectExecutor> closureEffectExecutors,
                                   List<AgentFollowUpExecutor> agentFollowUpExecutors) {
        this.businessActionExecutors = indexByKey(businessActionExecutors,
                BusinessActionExecutor::executorKey, "BusinessActionExecutor");
        this.closureEffectExecutors = indexByKey(closureEffectExecutors,
                ClosureEffectExecutor::executorKey, "ClosureEffectExecutor");
        this.agentFollowUpExecutors = indexByKey(agentFollowUpExecutors,
                AgentFollowUpExecutor::executorKey, "AgentFollowUpExecutor");
    }

    public boolean hasBusinessActionExecutor(String executorKey) {
        return StringUtils.hasText(executorKey) && businessActionExecutors.containsKey(executorKey);
    }

    public boolean hasClosureEffectExecutor(String executorKey) {
        return StringUtils.hasText(executorKey) && closureEffectExecutors.containsKey(executorKey);
    }

    public boolean hasBusinessOrClosureExecutor(String executorKey) {
        return hasBusinessActionExecutor(executorKey) || hasClosureEffectExecutor(executorKey);
    }

    public boolean hasAgentFollowUpExecutor(String executorKey) {
        return StringUtils.hasText(executorKey) && agentFollowUpExecutors.containsKey(executorKey);
    }

    public BusinessActionExecutor businessActionExecutor(String executorKey) {
        return businessActionExecutors.get(executorKey);
    }

    public ClosureEffectExecutor closureEffectExecutor(String executorKey) {
        return closureEffectExecutors.get(executorKey);
    }

    public AgentFollowUpExecutor agentFollowUpExecutor(String executorKey) {
        return agentFollowUpExecutors.get(executorKey);
    }

    public Collection<String> businessActionExecutorKeys() {
        return businessActionExecutors.keySet();
    }

    public Collection<String> closureEffectExecutorKeys() {
        return closureEffectExecutors.keySet();
    }

    public Collection<String> agentFollowUpExecutorKeys() {
        return agentFollowUpExecutors.keySet();
    }

    private <T> Map<String, T> indexByKey(List<T> executors, Function<T, String> keyFn,
                                          String executorType) {
        return executors.stream().collect(Collectors.toUnmodifiableMap(
                executor -> normalizeKey(keyFn.apply(executor), executorType),
                Function.identity(),
                (left, right) -> {
                    throw new IllegalStateException("Duplicate " + executorType
                            + " executorKey: " + keyFn.apply(left));
                }));
    }

    private String normalizeKey(String executorKey, String executorType) {
        if (!StringUtils.hasText(executorKey)) {
            throw new IllegalStateException(executorType + " executorKey is required");
        }
        return executorKey.trim();
    }

    public TreeSet<String> missingBusinessOrClosureExecutors(Collection<String> executorKeys) {
        return executorKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(key -> !hasBusinessOrClosureExecutor(key))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public TreeSet<String> missingAgentFollowUpExecutors(Collection<String> executorKeys) {
        return executorKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(key -> !hasAgentFollowUpExecutor(key))
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
