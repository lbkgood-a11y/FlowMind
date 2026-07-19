package com.triobase.service.action.service;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.service.action.entity.ActionExecution;
import com.triobase.service.action.exception.ActionRuntimeException;
import com.triobase.service.action.mapper.ActionExecutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ActionStatusService {

    private static final Map<ActionStatus, Set<ActionStatus>> ALLOWED_TRANSITIONS = Map.of(
            ActionStatus.CREATED, Set.of(ActionStatus.VALIDATING, ActionStatus.REJECTED, ActionStatus.CANCELLED),
            ActionStatus.VALIDATING, Set.of(ActionStatus.REJECTED, ActionStatus.AUTHORIZED),
            ActionStatus.AUTHORIZED, Set.of(ActionStatus.REJECTED, ActionStatus.ACCEPTED,
                    ActionStatus.RUNNING, ActionStatus.SUCCEEDED, ActionStatus.FAILED),
            ActionStatus.ACCEPTED, Set.of(ActionStatus.RUNNING, ActionStatus.SUCCEEDED,
                    ActionStatus.FAILED, ActionStatus.CANCELLED),
            ActionStatus.RUNNING, Set.of(ActionStatus.SUCCEEDED, ActionStatus.FAILED,
                    ActionStatus.CANCELLED, ActionStatus.COMPENSATING),
            ActionStatus.COMPENSATING, Set.of(ActionStatus.COMPENSATED, ActionStatus.FAILED)
    );

    private final ActionExecutionMapper actionExecutionMapper;

    public ActionExecution transition(ActionExecution execution, ActionStatus nextStatus) {
        ActionStatus current = ActionStatus.valueOf(execution.getStatus());
        if (current == nextStatus) {
            return execution;
        }
        if (current.terminal()) {
            throw transitionError(current, nextStatus, "ACTION_STATUS_TERMINAL");
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(nextStatus)) {
            throw transitionError(current, nextStatus, "ACTION_STATUS_TRANSITION_INVALID");
        }
        execution.setStatus(nextStatus.name());
        if (nextStatus.terminal()) {
            execution.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        actionExecutionMapper.updateById(execution);
        return execution;
    }

    private ActionRuntimeException transitionError(ActionStatus current,
                                                   ActionStatus next,
                                                   String message) {
        return new ActionRuntimeException(
                40943,
                ActionErrorCategory.CONFLICT,
                message,
                "status",
                null);
    }
}
