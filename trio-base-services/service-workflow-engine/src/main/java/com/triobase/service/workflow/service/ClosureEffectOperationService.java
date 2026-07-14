package com.triobase.service.workflow.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.workflow.dto.ProcessClosureDetailResponse;
import com.triobase.service.workflow.entity.ClosureEffect;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClosureEffectOperationService {

    private static final String RETRY_PERMISSION = "/api/v1/process-closures/*/retry:POST";

    private final ClosureEffectExecutionService closureEffectExecutionService;
    private final ProcessClosureQueryService processClosureQueryService;

    public ProcessClosureDetailResponse.EffectItem retry(String effectId) {
        if (!SecurityContextHolder.getPermissions().contains(RETRY_PERMISSION)) {
            throw new BizException(40300, "CLOSURE_RETRY_PERMISSION_DENIED");
        }
        ClosureEffect effect = closureEffectExecutionService.executeEffect(effectId);
        return toEffectItem(effect);
    }

    public ProcessClosureDetailResponse.EffectItem markHandled(String effectId, String reason) {
        if (!SecurityContextHolder.getPermissions().contains(RETRY_PERMISSION)) {
            throw new BizException(40300, "CLOSURE_RETRY_PERMISSION_DENIED");
        }
        ClosureEffect effect = closureEffectExecutionService.markManuallyHandled(
                effectId,
                reason,
                SecurityContextHolder.getUserId(),
                TraceUtil.getTraceId());
        return toEffectItem(effect);
    }

    private ProcessClosureDetailResponse.EffectItem toEffectItem(ClosureEffect effect) {
        ProcessClosureDetailResponse.EffectItem item = new ProcessClosureDetailResponse.EffectItem();
        item.setId(effect.getId());
        item.setEffectKey(effect.getEffectKey());
        item.setEffectType(effect.getEffectType());
        item.setTriggerOutcome(effect.getTriggerOutcome());
        item.setBusinessActionCode(effect.getBusinessActionCode());
        item.setBusinessActionName(effect.getBusinessActionCode());
        item.setExecutorKey(effect.getExecutorKey());
        item.setMode(effect.getMode());
        item.setStatus(effect.getStatus());
        item.setIdempotencyKey(effect.getIdempotencyKey());
        item.setResultJson(effect.getResultJson());
        item.setFailureCategory(effect.getFailureCategory());
        item.setLastError(effect.getLastError());
        item.setAttemptCount(effect.getAttemptCount());
        item.setNextRetryAt(effect.getNextRetryAt());
        item.setTraceId(effect.getTraceId());
        item.setRetryAvailable("FAILED".equals(effect.getStatus()) || "RETRYING".equals(effect.getStatus()));
        item.setManualHandlingAvailable(false);
        return item;
    }
}
