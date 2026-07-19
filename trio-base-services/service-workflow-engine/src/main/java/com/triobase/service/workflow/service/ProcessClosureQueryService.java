package com.triobase.service.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.ProcessClosureDetailResponse;
import com.triobase.service.workflow.entity.ClosureEffect;
import com.triobase.service.workflow.entity.ProcessClosure;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.ProcessOutcome;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.mapper.ClosureEffectMapper;
import com.triobase.service.workflow.mapper.ProcessInstanceMapper;
import com.triobase.service.workflow.mapper.ProcessClosureMapper;
import com.triobase.service.workflow.mapper.ProcessOutcomeMapper;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProcessClosureQueryService {

    private final ProcessOutcomeMapper processOutcomeMapper;
    private final ProcessClosureMapper processClosureMapper;
    private final ClosureEffectMapper closureEffectMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessPackageMapper processPackageMapper;
    private final ProcessBusinessAuthorizationService processBusinessAuthorizationService;
    private final ObjectMapper objectMapper;

    public ProcessClosureDetailResponse getByProcessInstanceId(String processInstanceId) {
        ProcessInstance instance = processInstanceMapper.selectById(processInstanceId);
        if (instance == null) {
            throw new BizException(40400, "PROCESS_INSTANCE_NOT_FOUND");
        }
        processBusinessAuthorizationService.requireCanView(instance);

        ProcessOutcome outcome = processOutcomeMapper.selectOne(new LambdaQueryWrapper<ProcessOutcome>()
                .eq(ProcessOutcome::getProcessInstanceId, processInstanceId)
                .orderByDesc(ProcessOutcome::getOutcomeVersion)
                .last("LIMIT 1"));
        if (outcome == null) {
            throw new BizException(40400, "PROCESS_OUTCOME_NOT_FOUND");
        }

        ProcessClosure closure = processClosureMapper.selectOne(new LambdaQueryWrapper<ProcessClosure>()
                .eq(ProcessClosure::getOutcomeId, outcome.getId())
                .last("LIMIT 1"));

        ProcessClosureDetailResponse response = new ProcessClosureDetailResponse();
        response.setOutcome(toOutcome(outcome));
        if (closure != null) {
            response.setClosure(toClosure(closure));
            Map<String, String> effectNames = effectDisplayNames(instance);
            response.setEffects(closureEffectMapper.selectList(new LambdaQueryWrapper<ClosureEffect>()
                            .eq(ClosureEffect::getClosureId, closure.getId())
                            .orderByAsc(ClosureEffect::getCreatedAt))
                    .stream().map(effect -> toEffect(effect, effectNames)).toList());
        }
        return response;
    }

    private ProcessClosureDetailResponse.OutcomeItem toOutcome(ProcessOutcome outcome) {
        ProcessClosureDetailResponse.OutcomeItem item =
                new ProcessClosureDetailResponse.OutcomeItem();
        item.setId(outcome.getId());
        item.setProcessInstanceId(outcome.getProcessInstanceId());
        item.setProcessKey(outcome.getProcessKey());
        item.setProcessVersion(outcome.getProcessVersion());
        item.setBusinessType(outcome.getBusinessType());
        item.setBusinessId(outcome.getBusinessId());
        item.setOutcomeStatus(outcome.getOutcomeStatus());
        item.setReason(outcome.getReason());
        item.setTenantId(outcome.getTenantId());
        item.setInitiatorId(outcome.getInitiatorId());
        item.setLastOperatorId(outcome.getLastOperatorId());
        item.setTraceId(outcome.getTraceId());
        item.setActionId(outcome.getActionId());
        item.setActionType(outcome.getActionType());
        item.setActionSource(outcome.getActionSource());
        item.setActionActorType(outcome.getActionActorType());
        item.setActionActorId(outcome.getActionActorId());
        item.setActionActorName(outcome.getActionActorName());
        item.setActionCorrelationId(outcome.getActionCorrelationId());
        item.setCreatedAt(outcome.getCreatedAt());
        return item;
    }

    private ProcessClosureDetailResponse.ClosureItem toClosure(ProcessClosure closure) {
        ProcessClosureDetailResponse.ClosureItem item =
                new ProcessClosureDetailResponse.ClosureItem();
        item.setId(closure.getId());
        item.setClosureStatus(closure.getClosureStatus());
        item.setBusinessType(closure.getBusinessType());
        item.setBusinessId(closure.getBusinessId());
        item.setTraceId(closure.getTraceId());
        item.setActionId(closure.getActionId());
        item.setActionType(closure.getActionType());
        item.setActionSource(closure.getActionSource());
        item.setActionActorType(closure.getActionActorType());
        item.setActionActorId(closure.getActionActorId());
        item.setActionActorName(closure.getActionActorName());
        item.setActionCorrelationId(closure.getActionCorrelationId());
        item.setStartedAt(closure.getStartedAt());
        item.setCompletedAt(closure.getCompletedAt());
        return item;
    }

    private ProcessClosureDetailResponse.EffectItem toEffect(ClosureEffect effect,
                                                             Map<String, String> effectNames) {
        ProcessClosureDetailResponse.EffectItem item =
                new ProcessClosureDetailResponse.EffectItem();
        item.setId(effect.getId());
        item.setEffectKey(effect.getEffectKey());
        item.setEffectType(effect.getEffectType());
        item.setTriggerOutcome(effect.getTriggerOutcome());
        item.setBusinessActionCode(effect.getBusinessActionCode());
        item.setBusinessActionName(effectNames.getOrDefault(effect.getEffectKey(),
                effectNames.getOrDefault(effect.getBusinessActionCode(), effect.getBusinessActionCode())));
        item.setExecutorKey(effect.getExecutorKey());
        item.setMode(effect.getMode());
        item.setStatus(effect.getStatus());
        item.setIdempotencyKey(effect.getIdempotencyKey());
        item.setRequestJson(effect.getRequestJson());
        item.setResultJson(effect.getResultJson());
        item.setFailureCategory(effect.getFailureCategory());
        item.setLastError(effect.getLastError());
        item.setAttemptCount(effect.getAttemptCount());
        item.setNextRetryAt(effect.getNextRetryAt());
        item.setTraceId(effect.getTraceId());
        item.setActionId(effect.getActionId());
        item.setActionType(effect.getActionType());
        item.setActionSource(effect.getActionSource());
        item.setActionActorType(effect.getActionActorType());
        item.setActionActorId(effect.getActionActorId());
        item.setActionActorName(effect.getActionActorName());
        item.setActionCorrelationId(effect.getActionCorrelationId());
        item.setRetryAvailable("FAILED".equals(effect.getStatus()) || "RETRYING".equals(effect.getStatus()));
        item.setManualHandlingAvailable(item.isRetryAvailable() && manualHandlingEnabled(effect));
        return item;
    }

    private Map<String, String> effectDisplayNames(ProcessInstance instance) {
        ProcessPackage pkg = processPackageMapper.selectById(instance.getProcessPackageId());
        if (pkg == null || !StringUtils.hasText(pkg.getClosurePlanJson())) {
            return Map.of();
        }
        Map<String, String> names = new LinkedHashMap<>();
        collectEffectNames(pkg.getClosurePlanJson(), names);
        if (StringUtils.hasText(pkg.getAgentFollowUpPlanJson())) {
            collectEffectNames(pkg.getAgentFollowUpPlanJson(), names);
        }
        return names;
    }

    private void collectEffectNames(String planJson, Map<String, String> names) {
        try {
            JsonNode root = objectMapper.readTree(planJson);
            collectEffectArray(root.path("failureEffects"), names);
            collectEffectArray(root.path("actions"), names);
            JsonNode outcomes = root.path("outcomes");
            if (outcomes.isObject()) {
                outcomes.fields().forEachRemaining(entry -> collectEffectArray(entry.getValue(), names));
            }
        } catch (JsonProcessingException ignored) {
            // Display names are best-effort; closure diagnostics still return effect codes.
        }
    }

    private void collectEffectArray(JsonNode effects, Map<String, String> names) {
        if (!effects.isArray()) {
            return;
        }
        for (JsonNode effect : effects) {
            String effectKey = effect.path("effectKey").asText(null);
            String code = null;
            String displayName = null;
            if (effect.hasNonNull("action")) {
                code = effect.path("action").path("actionCode").asText(null);
                displayName = effect.path("action").path("displayName").asText(null);
            } else if (effect.hasNonNull("agentAction")) {
                code = effect.path("agentAction").path("agentActionCode").asText(null);
                displayName = effect.path("agentAction").path("displayName").asText(null);
            } else if (effect.hasNonNull("event")) {
                code = effect.path("event").path("eventCode").asText(null);
                displayName = effect.path("event").path("displayName").asText(null);
            }
            if (StringUtils.hasText(effectKey) && StringUtils.hasText(displayName)) {
                names.put(effectKey, displayName);
            }
            if (StringUtils.hasText(code) && StringUtils.hasText(displayName)) {
                names.put(code, displayName);
            }
        }
    }

    private boolean manualHandlingEnabled(ClosureEffect effect) {
        if (!StringUtils.hasText(effect.getRequestJson())) {
            return false;
        }
        try {
            JsonNode params = objectMapper.readTree(effect.getRequestJson());
            return params.path("manualHandlingEnabled").asBoolean(false)
                    || params.path("allowManualHandling").asBoolean(false);
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }
}
