package com.triobase.service.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.workflow.entity.ClosureEffect;
import com.triobase.service.workflow.entity.ClosureOutbox;
import com.triobase.service.workflow.entity.ProcessBusinessEvent;
import com.triobase.service.workflow.entity.ProcessClosure;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.ProcessOutcome;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.entity.TaskOperation;
import com.triobase.service.workflow.mapper.ClosureEffectMapper;
import com.triobase.service.workflow.mapper.ClosureOutboxMapper;
import com.triobase.service.workflow.mapper.ProcessBusinessEventMapper;
import com.triobase.service.workflow.mapper.ProcessClosureMapper;
import com.triobase.service.workflow.mapper.ProcessInstanceMapper;
import com.triobase.service.workflow.mapper.ProcessOutcomeMapper;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import com.triobase.service.workflow.mapper.TaskOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessOutcomeService {

    private static final int OUTCOME_VERSION = 1;

    private final ProcessOutcomeMapper processOutcomeMapper;
    private final ProcessClosureMapper processClosureMapper;
    private final ClosureEffectMapper closureEffectMapper;
    private final ClosureOutboxMapper closureOutboxMapper;
    private final ProcessBusinessEventMapper processBusinessEventMapper;
    private final ClosureEffectExecutionService closureEffectExecutionService;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessPackageMapper processPackageMapper;
    private final TaskOperationMapper taskOperationMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProcessOutcome createOutcome(String processInstanceId,
                                        String outcomeStatus,
                                        String reason) {
        ProcessOutcome existing = findOutcome(processInstanceId);
        if (existing != null) {
            return existing;
        }

        ProcessInstance instance = processInstanceMapper.selectById(processInstanceId);
        if (instance == null) {
            throw new BizException(40400, "PROCESS_INSTANCE_NOT_FOUND");
        }
        ProcessPackage pkg = processPackageMapper.selectById(instance.getProcessPackageId());
        if (pkg == null) {
            throw new BizException(40400, "PROCESS_PACKAGE_NOT_FOUND");
        }

        ProcessOutcome outcome = new ProcessOutcome();
        outcome.setId(UlidGenerator.nextUlid());
        outcome.setProcessInstanceId(processInstanceId);
        outcome.setOutcomeVersion(OUTCOME_VERSION);
        outcome.setProcessPackageId(pkg.getId());
        outcome.setProcessKey(instance.getProcessKey());
        outcome.setProcessVersion(instance.getVersion());
        outcome.setBusinessType(instance.getBusinessType());
        outcome.setBusinessId(instance.getBusinessId());
        outcome.setOutcomeStatus(outcomeStatus);
        outcome.setResultCode(outcomeStatus);
        outcome.setReason(reason);
        outcome.setTenantId(StringUtils.hasText(instance.getTenantId())
                ? instance.getTenantId() : "GLOBAL");
        outcome.setInitiatorId(instance.getInitiatorId());
        outcome.setLastOperatorId(lastOperatorId(processInstanceId, instance.getInitiatorId()));
        outcome.setTraceId(TraceUtil.getTraceId());
        outcome.setPayloadJson(writeJson(Map.of(
                "processInstanceId", processInstanceId,
                "processKey", instance.getProcessKey(),
                "processVersion", instance.getVersion(),
                "businessType", instance.getBusinessType() == null ? "" : instance.getBusinessType(),
                "businessId", instance.getBusinessId() == null ? "" : instance.getBusinessId(),
                "outcomeStatus", outcomeStatus,
                "reason", reason == null ? "" : reason)));

        try {
            processOutcomeMapper.insert(outcome);
        } catch (DuplicateKeyException duplicate) {
            ProcessOutcome persisted = findOutcome(processInstanceId);
            if (persisted == null) {
                throw duplicate;
            }
            return persisted;
        }

        createOutcomeEvent(outcome);
        createClosureRecords(outcome, instance, pkg);
        return outcome;
    }

    private ProcessOutcome findOutcome(String processInstanceId) {
        return processOutcomeMapper.selectOne(new LambdaQueryWrapper<ProcessOutcome>()
                .eq(ProcessOutcome::getProcessInstanceId, processInstanceId)
                .eq(ProcessOutcome::getOutcomeVersion, OUTCOME_VERSION)
                .last("LIMIT 1"));
    }

    private String lastOperatorId(String processInstanceId, String fallback) {
        TaskOperation operation = taskOperationMapper.selectOne(new LambdaQueryWrapper<TaskOperation>()
                .eq(TaskOperation::getProcessInstanceId, processInstanceId)
                .orderByDesc(TaskOperation::getCreatedAt)
                .last("LIMIT 1"));
        return operation != null && StringUtils.hasText(operation.getOperatorId())
                ? operation.getOperatorId()
                : fallback;
    }

    private void createClosureRecords(ProcessOutcome outcome,
                                      ProcessInstance instance,
                                      ProcessPackage pkg) {
        if (!StringUtils.hasText(pkg.getClosurePlanJson())) {
            return;
        }
        if (processClosureMapper.selectOne(new LambdaQueryWrapper<ProcessClosure>()
                .eq(ProcessClosure::getOutcomeId, outcome.getId())
                .last("LIMIT 1")) != null) {
            return;
        }

        JsonNode closurePlan = readTree(pkg.getClosurePlanJson());
        JsonNode effects = closurePlan.path("outcomes").path(outcome.getOutcomeStatus());
        ProcessClosure closure = new ProcessClosure();
        closure.setId(UlidGenerator.nextUlid());
        closure.setOutcomeId(outcome.getId());
        closure.setProcessInstanceId(instance.getId());
        closure.setBusinessType(outcome.getBusinessType());
        closure.setBusinessId(outcome.getBusinessId());
        closure.setClosureStatus(effects.isArray() && !effects.isEmpty() ? "PENDING" : "SKIPPED");
        closure.setTraceId(outcome.getTraceId());
        closure.setStartedAt(LocalDateTime.now());
        if (!effects.isArray() || effects.isEmpty()) {
            closure.setCompletedAt(LocalDateTime.now());
        }
        processClosureMapper.insert(closure);
        createClosureEvent(outcome, closure);

        if (effects.isArray()) {
            int index = 0;
            for (JsonNode effect : effects) {
                ClosureEffect closureEffect = toEffect(closure, outcome, effect, index);
                closureEffectMapper.insert(closureEffect);
                if ("HARD".equals(closureEffect.getMode())) {
                    closureEffectExecutionService.executeEffect(closureEffect.getId());
                } else {
                    createOutbox(closure, closureEffect, outcome);
                }
                index++;
            }
        }
    }

    private void createOutbox(ProcessClosure closure,
                              ClosureEffect effect,
                              ProcessOutcome outcome) {
        ClosureOutbox outbox = new ClosureOutbox();
        outbox.setId(UlidGenerator.nextUlid());
        outbox.setClosureId(closure.getId());
        outbox.setEffectId(effect.getId());
        outbox.setEventType("ClosureEffectRequested");
        outbox.setPayloadJson(writeJson(Map.of(
                "outcomeId", outcome.getId(),
                "closureId", closure.getId(),
                "effectId", effect.getId(),
                "effectType", effect.getEffectType(),
                "businessType", outcome.getBusinessType() == null ? "" : outcome.getBusinessType(),
                "businessId", outcome.getBusinessId() == null ? "" : outcome.getBusinessId())));
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setTraceId(outcome.getTraceId());
        closureOutboxMapper.insert(outbox);
    }

    private void createOutcomeEvent(ProcessOutcome outcome) {
        Map<String, Object> payload = eventPayload(outcome);
        payload.put("eventType", "ProcessOutcomeCreated");
        ProcessBusinessEvent event = baseEvent(
                outcome,
                "ProcessOutcomeCreated",
                outcome.getId() + ":outcome");
        event.setPayloadJson(writeJson(payload));
        insertBusinessEvent(event);
    }

    private void createClosureEvent(ProcessOutcome outcome, ProcessClosure closure) {
        Map<String, Object> payload = eventPayload(outcome);
        payload.put("eventType", "ProcessClosureCreated");
        payload.put("closureId", closure.getId());
        payload.put("closureStatus", closure.getClosureStatus());
        ProcessBusinessEvent event = baseEvent(
                outcome,
                "ProcessClosureCreated",
                closure.getId() + ":closure");
        event.setProcessClosureId(closure.getId());
        event.setClosureStatus(closure.getClosureStatus());
        event.setPayloadJson(writeJson(payload));
        insertBusinessEvent(event);
    }

    private ProcessBusinessEvent baseEvent(ProcessOutcome outcome,
                                           String eventType,
                                           String eventKey) {
        ProcessBusinessEvent event = new ProcessBusinessEvent();
        event.setId(UlidGenerator.nextUlid());
        event.setEventKey(eventKey);
        event.setEventType(eventType);
        event.setTenantId(outcome.getTenantId());
        event.setProcessInstanceId(outcome.getProcessInstanceId());
        event.setProcessOutcomeId(outcome.getId());
        event.setProcessKey(outcome.getProcessKey());
        event.setProcessVersion(outcome.getProcessVersion());
        event.setBusinessType(outcome.getBusinessType());
        event.setBusinessId(outcome.getBusinessId());
        event.setOutcomeStatus(outcome.getOutcomeStatus());
        event.setTraceId(outcome.getTraceId());
        event.setStatus("AVAILABLE");
        return event;
    }

    private Map<String, Object> eventPayload(ProcessOutcome outcome) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outcomeId", outcome.getId());
        payload.put("processInstanceId", outcome.getProcessInstanceId());
        payload.put("processPackageId", outcome.getProcessPackageId());
        payload.put("processKey", outcome.getProcessKey());
        payload.put("processVersion", outcome.getProcessVersion());
        payload.put("businessType", outcome.getBusinessType());
        payload.put("businessId", outcome.getBusinessId());
        payload.put("outcomeStatus", outcome.getOutcomeStatus());
        payload.put("tenantId", outcome.getTenantId());
        payload.put("initiatorId", outcome.getInitiatorId());
        payload.put("lastOperatorId", outcome.getLastOperatorId());
        payload.put("traceId", outcome.getTraceId());
        return payload;
    }

    private void insertBusinessEvent(ProcessBusinessEvent event) {
        try {
            processBusinessEventMapper.insert(event);
        } catch (DuplicateKeyException duplicate) {
            log.debug("Business process event already exists: eventKey={}", event.getEventKey());
        }
    }

    private ClosureEffect toEffect(ProcessClosure closure,
                                   ProcessOutcome outcome,
                                   JsonNode effectNode,
                                   int index) {
        String selectorType = effectNode.path("selectorType").asText();
        JsonNode selector = selector(selectorType, effectNode);
        String selectorCode = selectorCode(selectorType, selector);
        String mode = firstText(effectNode.path("mode").asText(null),
                selector.path("modeDefault").asText("ASYNC"));
        String effectKey = firstText(effectNode.path("effectKey").asText(null),
                outcome.getOutcomeStatus() + "." + selectorCode + "." + index);

        ClosureEffect effect = new ClosureEffect();
        effect.setId(UlidGenerator.nextUlid());
        effect.setClosureId(closure.getId());
        effect.setEffectKey(effectKey);
        effect.setEffectType(effectType(selectorType, selector));
        effect.setTriggerOutcome(outcome.getOutcomeStatus());
        effect.setBusinessActionCode(selectorCode);
        effect.setExecutorKey(selector.path("executorKey").asText(null));
        effect.setMode(mode);
        effect.setStatus("PENDING");
        effect.setIdempotencyKey(outcome.getProcessInstanceId()
                + ":" + outcome.getOutcomeStatus() + ":" + effectKey);
        effect.setRequestJson(writeJson(effectNode.path("params")));
        effect.setAttemptCount(0);
        effect.setOperatorId(outcome.getLastOperatorId());
        effect.setTraceId(outcome.getTraceId());
        return effect;
    }

    private JsonNode selector(String selectorType, JsonNode effectNode) {
        return switch (selectorType) {
            case "ACTION" -> effectNode.path("action");
            case "AGENT_ACTION" -> effectNode.path("agentAction");
            case "EVENT" -> effectNode.path("event");
            default -> objectMapper.createObjectNode();
        };
    }

    private String selectorCode(String selectorType, JsonNode selector) {
        return switch (selectorType) {
            case "ACTION" -> selector.path("actionCode").asText("action");
            case "AGENT_ACTION" -> selector.path("agentActionCode").asText("agentAction");
            case "EVENT" -> selector.path("eventCode").asText("event");
            default -> "effect";
        };
    }

    private String effectType(String selectorType, JsonNode selector) {
        if ("AGENT_ACTION".equals(selectorType)) {
            return "AGENT_FOLLOW_UP";
        }
        if ("EVENT".equals(selectorType)) {
            return "DOMAIN_EVENT";
        }
        return switch (selector.path("actionType").asText()) {
            case "CREATE_DOCUMENT" -> "CREATE_DOCUMENT";
            case "UPDATE_STATUS" -> "BUSINESS_STATUS_UPDATE";
            case "DOMAIN_EVENT" -> "DOMAIN_EVENT";
            case "NOTIFICATION" -> "NOTIFICATION";
            default -> "BUSINESS_STATUS_UPDATE";
        };
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BizException(50000, "INVALID_CLOSURE_PLAN");
        }
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(50000, "OUTCOME_SERIALIZATION_FAILED");
        }
    }
}
