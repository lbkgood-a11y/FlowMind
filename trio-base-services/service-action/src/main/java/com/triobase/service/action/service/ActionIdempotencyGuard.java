package com.triobase.service.action.service;

import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.util.ActionIdempotencyKeys;
import com.triobase.common.action.util.ActionTypeValidator;
import com.triobase.service.action.repository.ActionExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ActionIdempotencyGuard {

    private static final String GLOBAL_TENANT = "GLOBAL";

    private final ActionExecutionRepository actionExecutionRepository;
    private final ActionResultFactory actionResultFactory;

    public Optional<GlobalActionResult> duplicateResult(GlobalActionRequest request) {
        String idempotencyKey = ActionIdempotencyKeys.normalize(request.getIdempotencyKey());
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        String actionType = ActionTypeValidator.requireValid(request.getActionType());
        return actionExecutionRepository
                .findByIdempotency(resolveTenantId(request), actionType, idempotencyKey)
                .map(actionResultFactory::fromExecution);
    }

    private String resolveTenantId(GlobalActionRequest request) {
        ActionContext context = request.getContext();
        ActionTarget target = request.getTarget();
        ActionActor actor = request.getActor();
        return firstNonBlank(
                context != null ? context.getTenantId() : null,
                target != null ? target.getTenantId() : null,
                actor != null ? actor.getTenantId() : null,
                GLOBAL_TENANT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return GLOBAL_TENANT;
    }
}
