package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.definition.ActionRetryPolicy;
import com.triobase.common.action.util.ActionPayloadRedactor;
import com.triobase.service.action.entity.ActionDispatch;
import com.triobase.service.action.entity.ActionExecution;
import com.triobase.service.action.mapper.ActionDispatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActionDispatchService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DISPATCHED = "DISPATCHED";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_RETRY_WAITING = "RETRY_WAITING";

    private static final String DISPATCH_PREFIX = "dsp_";

    private final ActionDispatchMapper actionDispatchMapper;

    public ActionDispatch createDispatch(ActionExecution execution, ActionDefinition definition) {
        ActionDispatch dispatch = new ActionDispatch();
        dispatch.setId(newDispatchId());
        dispatch.setActionId(execution.getId());
        dispatch.setTenantId(execution.getTenantId());
        dispatch.setOwnerService(definition.getOwnerService());
        dispatch.setDispatchStatus(STATUS_PENDING);
        dispatch.setAttemptCount(0);
        dispatch.setMaxAttempts(maxAttempts(definition.getRetryPolicy()));
        actionDispatchMapper.insert(dispatch);
        return dispatch;
    }

    public ActionDispatch markDispatched(ActionDispatch dispatch) {
        dispatch.setDispatchStatus(STATUS_DISPATCHED);
        dispatch.setAttemptCount((dispatch.getAttemptCount() == null ? 0 : dispatch.getAttemptCount()) + 1);
        dispatch.setDispatchedAt(LocalDateTime.now(ZoneOffset.UTC));
        actionDispatchMapper.updateById(dispatch);
        return dispatch;
    }

    public ActionDispatch markCompleted(ActionDispatch dispatch) {
        dispatch.setDispatchStatus(STATUS_SUCCEEDED);
        dispatch.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        actionDispatchMapper.updateById(dispatch);
        return dispatch;
    }

    public ActionDispatch markFailed(ActionDispatch dispatch, String errorMessage, boolean retryable) {
        dispatch.setLastError(ActionPayloadRedactor.boundedSummary(errorMessage, 1024));
        boolean canRetry = retryable
                && dispatch.getAttemptCount() != null
                && dispatch.getMaxAttempts() != null
                && dispatch.getAttemptCount() < dispatch.getMaxAttempts();
        dispatch.setDispatchStatus(canRetry ? STATUS_RETRY_WAITING : STATUS_FAILED);
        if (!canRetry) {
            dispatch.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        actionDispatchMapper.updateById(dispatch);
        return dispatch;
    }

    private int maxAttempts(ActionRetryPolicy retryPolicy) {
        if (retryPolicy == null || retryPolicy.getMaxAttempts() <= 0) {
            return 1;
        }
        return retryPolicy.getMaxAttempts();
    }

    private String newDispatchId() {
        return DISPATCH_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
