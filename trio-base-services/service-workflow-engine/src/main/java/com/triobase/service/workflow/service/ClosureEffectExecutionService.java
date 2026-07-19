package com.triobase.service.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.action.WorkflowActionExecutionContext;
import com.triobase.service.workflow.entity.ClosureEffect;
import com.triobase.service.workflow.entity.ClosureOutbox;
import com.triobase.service.workflow.entity.ProcessClosure;
import com.triobase.service.workflow.entity.ProcessOutcome;
import com.triobase.service.workflow.executor.AgentFollowUpContext;
import com.triobase.service.workflow.executor.AgentFollowUpExecutor;
import com.triobase.service.workflow.executor.AgentFollowUpResult;
import com.triobase.service.workflow.executor.BusinessActionContext;
import com.triobase.service.workflow.executor.BusinessActionExecutor;
import com.triobase.service.workflow.executor.BusinessActionResult;
import com.triobase.service.workflow.executor.ClosureEffectContext;
import com.triobase.service.workflow.executor.ClosureEffectExecutor;
import com.triobase.service.workflow.executor.ClosureEffectResult;
import com.triobase.service.workflow.executor.ProcessExecutorRegistry;
import com.triobase.service.workflow.mapper.ClosureEffectMapper;
import com.triobase.service.workflow.mapper.ClosureOutboxMapper;
import com.triobase.service.workflow.mapper.ProcessClosureMapper;
import com.triobase.service.workflow.mapper.ProcessOutcomeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClosureEffectExecutionService {

    private static final int MAX_ATTEMPTS = 3;

    private final ClosureEffectMapper closureEffectMapper;
    private final ProcessClosureMapper processClosureMapper;
    private final ProcessOutcomeMapper processOutcomeMapper;
    private final ClosureOutboxMapper closureOutboxMapper;
    private final ProcessExecutorRegistry executorRegistry;
    private final ObjectMapper objectMapper;

    @Transactional
    public ClosureEffect executeEffect(String effectId) {
        ClosureEffect effect = closureEffectMapper.selectById(effectId);
        if (effect == null) {
            throw new BizException(40400, "CLOSURE_EFFECT_NOT_FOUND");
        }
        if ("SUCCEEDED".equals(effect.getStatus()) || "MANUALLY_HANDLED".equals(effect.getStatus())) {
            return effect;
        }

        ProcessClosure closure = processClosureMapper.selectById(effect.getClosureId());
        if (closure == null) {
            return fail(effect, null, "CLOSURE_NOT_FOUND", "Closure record not found");
        }
        ProcessOutcome outcome = processOutcomeMapper.selectById(closure.getOutcomeId());
        if (outcome == null) {
            return fail(effect, closure, "OUTCOME_NOT_FOUND", "Process outcome not found");
        }

        effect.setStatus("RUNNING");
        effect.setStartedAt(LocalDateTime.now());
        applyCurrentActionMetadata(effect);
        closureEffectMapper.updateById(effect);

        ExecutionResult result = invokeExecutor(effect, closure, outcome);
        if (result.success()) {
            return succeed(effect, closure, result);
        }
        return fail(effect, closure, result.resultCode(), result.message());
    }

    @Transactional
    public ClosureEffect markManuallyHandled(String effectId,
                                             String reason,
                                             String operatorId,
                                             String traceId) {
        ClosureEffect effect = closureEffectMapper.selectById(effectId);
        if (effect == null) {
            throw new BizException(40400, "CLOSURE_EFFECT_NOT_FOUND");
        }
        if ("SUCCEEDED".equals(effect.getStatus()) || "MANUALLY_HANDLED".equals(effect.getStatus())) {
            return effect;
        }
        if (!manualHandlingEnabled(effect)) {
            throw new BizException(40000, "CLOSURE_MANUAL_HANDLING_NOT_ENABLED");
        }
        if (!List.of("FAILED", "RETRYING").contains(effect.getStatus())) {
            throw new BizException(40000, "CLOSURE_EFFECT_NOT_FAILED");
        }
        ProcessClosure closure = processClosureMapper.selectById(effect.getClosureId());
        if (closure == null) {
            throw new BizException(40400, "CLOSURE_NOT_FOUND");
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("resultCode", "MANUALLY_HANDLED");
        audit.put("message", reason == null ? "" : reason);
        audit.put("operatorId", operatorId == null ? "" : operatorId);
        audit.put("traceId", traceId == null ? "" : traceId);
        audit.put("handledAt", LocalDateTime.now().toString());
        audit.put("originalStatus", effect.getStatus());
        audit.put("originalFailureCategory", effect.getFailureCategory());
        audit.put("originalLastError", effect.getLastError());

        effect.setStatus("MANUALLY_HANDLED");
        effect.setResultJson(writeJson(audit));
        effect.setFailureCategory("MANUALLY_HANDLED");
        effect.setLastError(null);
        effect.setNextRetryAt(null);
        effect.setOperatorId(operatorId);
        if (StringUtils.hasText(traceId)) {
            effect.setTraceId(traceId);
        }
        applyCurrentActionMetadata(effect);
        effect.setCompletedAt(LocalDateTime.now());
        closureEffectMapper.updateById(effect);

        List<ClosureOutbox> outboxes = closureOutboxMapper.selectList(
                new LambdaQueryWrapper<ClosureOutbox>()
                        .eq(ClosureOutbox::getEffectId, effectId)
                        .in(ClosureOutbox::getStatus, List.of("PENDING", "RUNNING", "FAILED", "RETRYING")));
        for (ClosureOutbox outbox : outboxes) {
            outbox.setStatus("SKIPPED");
            outbox.setLastError("Manually handled: " + (reason == null ? "" : reason));
            outbox.setPublishedAt(LocalDateTime.now());
            closureOutboxMapper.updateById(outbox);
        }

        recalculateClosureStatus(closure.getId());
        return effect;
    }

    @Transactional
    public int dispatchPendingOutbox(int limit) {
        List<ClosureOutbox> records = closureOutboxMapper.selectList(
                new LambdaQueryWrapper<ClosureOutbox>()
                        .in(ClosureOutbox::getStatus, List.of("PENDING", "RETRYING"))
                        .orderByAsc(ClosureOutbox::getCreatedAt)
                        .last("LIMIT " + Math.max(1, limit)));
        int dispatched = 0;
        for (ClosureOutbox outbox : records) {
            dispatched++;
            outbox.setStatus("RUNNING");
            outbox.setAttemptCount(outbox.getAttemptCount() == null ? 1 : outbox.getAttemptCount() + 1);
            outbox.setLockedAt(LocalDateTime.now());
            closureOutboxMapper.updateById(outbox);

            try {
                ClosureEffect effect = executeEffect(outbox.getEffectId());
                outbox.setStatus("SUCCEEDED".equals(effect.getStatus()) ? "SUCCEEDED" : effect.getStatus());
                outbox.setNextRetryAt(effect.getNextRetryAt());
                outbox.setLastError(effect.getLastError());
                if ("SUCCEEDED".equals(effect.getStatus())) {
                    outbox.setPublishedAt(LocalDateTime.now());
                }
            } catch (RuntimeException ex) {
                outbox.setStatus("FAILED");
                outbox.setLastError(ex.getMessage());
            }
            closureOutboxMapper.updateById(outbox);
        }
        return dispatched;
    }

    private ExecutionResult invokeExecutor(ClosureEffect effect,
                                           ProcessClosure closure,
                                           ProcessOutcome outcome) {
        if (!StringUtils.hasText(effect.getExecutorKey())) {
            return ExecutionResult.failed("CLOSURE_EFFECT_EXECUTOR_MISSING",
                    "Closure effect has no executorKey");
        }

        ClosureEffectExecutor closureExecutor =
                executorRegistry.closureEffectExecutor(effect.getExecutorKey());
        if (closureExecutor != null) {
            ClosureEffectResult result = closureExecutor.execute(new ClosureEffectContext(
                    outcome.getTenantId(),
                    closure.getId(),
                    effect.getId(),
                    outcome.getBusinessType(),
                    outcome.getBusinessId(),
                    effect.getEffectKey(),
                    effect.getTriggerOutcome(),
                    params(effect),
                    effect.getIdempotencyKey(),
                    effect.getTraceId(),
                    effect.getOperatorId()));
            return new ExecutionResult(result.success(), result.resultCode(),
                    result.message(), result.data());
        }

        BusinessActionExecutor businessExecutor =
                executorRegistry.businessActionExecutor(effect.getExecutorKey());
        if (businessExecutor != null) {
            BusinessActionResult result = businessExecutor.execute(new BusinessActionContext(
                    outcome.getTenantId(),
                    outcome.getBusinessType(),
                    outcome.getBusinessId(),
                    effect.getBusinessActionCode(),
                    params(effect),
                    effect.getIdempotencyKey(),
                    effect.getTraceId(),
                    effect.getOperatorId()));
            return new ExecutionResult(result.success(), result.resultCode(),
                    result.message(), result.data());
        }

        AgentFollowUpExecutor agentExecutor =
                executorRegistry.agentFollowUpExecutor(effect.getExecutorKey());
        if (agentExecutor != null) {
            AgentFollowUpResult result = agentExecutor.execute(new AgentFollowUpContext(
                    outcome.getTenantId(),
                    outcome.getBusinessType(),
                    outcome.getBusinessId(),
                    effect.getBusinessActionCode(),
                    params(effect),
                    effect.getIdempotencyKey(),
                    effect.getTraceId(),
                    effect.getOperatorId(),
                    payload(outcome)));
            return new ExecutionResult(result.success(), result.resultCode(),
                    result.summary(), result.data());
        }

        return ExecutionResult.failed("CLOSURE_EFFECT_EXECUTOR_NOT_REGISTERED",
                "No registered executor for " + effect.getExecutorKey());
    }

    private ClosureEffect succeed(ClosureEffect effect,
                                  ProcessClosure closure,
                                  ExecutionResult result) {
        effect.setStatus("SUCCEEDED");
        effect.setAttemptCount(effect.getAttemptCount() == null ? 1 : effect.getAttemptCount() + 1);
        effect.setResultJson(writeJson(resultPayload(result)));
        effect.setLastError(null);
        effect.setCompletedAt(LocalDateTime.now());
        closureEffectMapper.updateById(effect);
        recalculateClosureStatus(closure.getId());
        return effect;
    }

    private ClosureEffect fail(ClosureEffect effect,
                               ProcessClosure closure,
                               String resultCode,
                               String message) {
        int attempts = effect.getAttemptCount() == null ? 1 : effect.getAttemptCount() + 1;
        effect.setAttemptCount(attempts);
        effect.setFailureCategory(resultCode);
        effect.setLastError(message);
        effect.setCompletedAt(LocalDateTime.now());
        if (!"HARD".equals(effect.getMode()) && attempts < MAX_ATTEMPTS) {
            effect.setStatus("RETRYING");
            effect.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
        } else {
            effect.setStatus("FAILED");
            effect.setNextRetryAt(null);
        }
        closureEffectMapper.updateById(effect);
        if (closure != null) {
            recalculateClosureStatus(closure.getId());
        }
        return effect;
    }

    private void recalculateClosureStatus(String closureId) {
        ProcessClosure closure = processClosureMapper.selectById(closureId);
        if (closure == null) {
            return;
        }
        List<ClosureEffect> effects = closureEffectMapper.selectList(
                new LambdaQueryWrapper<ClosureEffect>()
                        .eq(ClosureEffect::getClosureId, closureId));
        if (effects.isEmpty()) {
            closure.setClosureStatus("SKIPPED");
            closure.setCompletedAt(LocalDateTime.now());
        } else if (effects.stream().allMatch(effect ->
                "SUCCEEDED".equals(effect.getStatus())
                        || "SKIPPED".equals(effect.getStatus())
                        || "MANUALLY_HANDLED".equals(effect.getStatus()))) {
            closure.setClosureStatus("SUCCEEDED");
            closure.setCompletedAt(LocalDateTime.now());
        } else if (effects.stream().anyMatch(effect -> "FAILED".equals(effect.getStatus()))) {
            boolean hardFailed = effects.stream()
                    .anyMatch(effect -> "FAILED".equals(effect.getStatus())
                            && "HARD".equals(effect.getMode()));
            closure.setClosureStatus(hardFailed ? "FAILED" : "PARTIAL_FAILED");
        } else {
            closure.setClosureStatus("RUNNING");
        }
        applyCurrentActionMetadata(closure);
        processClosureMapper.updateById(closure);
    }

    private void applyCurrentActionMetadata(ClosureEffect effect) {
        WorkflowActionExecutionContext.Snapshot snapshot = WorkflowActionExecutionContext.current();
        if (snapshot == null) {
            return;
        }
        effect.setActionId(snapshot.actionId());
        effect.setActionType(snapshot.actionType());
        effect.setActionSource(snapshot.source());
        effect.setActionActorType(snapshot.actorType());
        effect.setActionActorId(snapshot.actorId());
        effect.setActionActorName(snapshot.actorName());
        effect.setActionCorrelationId(snapshot.correlationId());
    }

    private void applyCurrentActionMetadata(ProcessClosure closure) {
        WorkflowActionExecutionContext.Snapshot snapshot = WorkflowActionExecutionContext.current();
        if (snapshot == null) {
            return;
        }
        closure.setActionId(snapshot.actionId());
        closure.setActionType(snapshot.actionType());
        closure.setActionSource(snapshot.source());
        closure.setActionActorType(snapshot.actorType());
        closure.setActionActorId(snapshot.actorId());
        closure.setActionActorName(snapshot.actorName());
        closure.setActionCorrelationId(snapshot.correlationId());
    }

    private Map<String, Object> params(ClosureEffect effect) {
        if (!StringUtils.hasText(effect.getRequestJson())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(effect.getRequestJson(), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private boolean manualHandlingEnabled(ClosureEffect effect) {
        Map<String, Object> params = params(effect);
        return Boolean.TRUE.equals(params.get("manualHandlingEnabled"))
                || Boolean.TRUE.equals(params.get("allowManualHandling"));
    }

    private Map<String, Object> payload(ProcessOutcome outcome) {
        if (!StringUtils.hasText(outcome.getPayloadJson())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(outcome.getPayloadJson(), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Object> resultPayload(ExecutionResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resultCode", result.resultCode());
        payload.put("message", result.message());
        payload.put("data", result.data() == null ? Map.of() : result.data());
        return payload;
    }

    private record ExecutionResult(boolean success,
                                   String resultCode,
                                   String message,
                                   Map<String, Object> data) {

        private static ExecutionResult failed(String resultCode, String message) {
            return new ExecutionResult(false, resultCode, message, Map.of());
        }
    }
}
