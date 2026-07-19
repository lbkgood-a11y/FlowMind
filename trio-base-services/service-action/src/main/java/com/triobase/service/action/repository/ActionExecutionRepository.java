package com.triobase.service.action.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionAuditLevel;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.util.ActionCorrelationIds;
import com.triobase.common.action.util.ActionIdempotencyKeys;
import com.triobase.common.action.util.ActionTypeValidator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.action.dto.ActionQueryCriteria;
import com.triobase.service.action.entity.ActionExecution;
import com.triobase.service.action.mapper.ActionExecutionMapper;
import com.triobase.service.action.support.ActionJsonSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ActionExecutionRepository {

    private static final String GLOBAL_TENANT = "GLOBAL";

    private final ActionExecutionMapper actionExecutionMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreateResult createIfAbsent(GlobalActionRequest request) {
        return createIfAbsent(request, ActionJsonSupport.boundedJson(objectMapper, request.getPayload()));
    }

    @Transactional
    public CreateResult createIfAbsent(GlobalActionRequest request, String payloadSummary) {
        String tenantId = resolveTenantId(request);
        String actionType = ActionTypeValidator.requireValid(request.getActionType());
        String idempotencyKey = ActionIdempotencyKeys.normalize(request.getIdempotencyKey());
        if (idempotencyKey != null) {
            Optional<ActionExecution> existing = findByIdempotency(tenantId, actionType, idempotencyKey);
            if (existing.isPresent()) {
                return new CreateResult(existing.get(), false);
            }
        }

        ActionExecution execution = toExecution(request, tenantId, actionType, idempotencyKey, payloadSummary);
        try {
            actionExecutionMapper.insert(execution);
            return new CreateResult(execution, true);
        } catch (DuplicateKeyException ex) {
            if (idempotencyKey == null) {
                throw ex;
            }
            return findByIdempotency(tenantId, actionType, idempotencyKey)
                    .map(existing -> new CreateResult(existing, false))
                    .orElseThrow(() -> ex);
        }
    }

    public Optional<ActionExecution> findById(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(actionExecutionMapper.selectById(actionId.trim()));
    }

    public Optional<ActionExecution> findByIdempotency(String tenantId,
                                                       String actionType,
                                                       String idempotencyKey) {
        String normalizedKey = ActionIdempotencyKeys.normalize(idempotencyKey);
        if (normalizedKey == null) {
            return Optional.empty();
        }
        String normalizedActionType = ActionTypeValidator.requireValid(actionType);
        return Optional.ofNullable(actionExecutionMapper.selectOne(new LambdaQueryWrapper<ActionExecution>()
                .eq(ActionExecution::getTenantId, normalizeTenant(tenantId))
                .eq(ActionExecution::getActionType, normalizedActionType)
                .eq(ActionExecution::getIdempotencyKey, normalizedKey)
                .last("LIMIT 1")));
    }

    public PageResult<ActionExecution> query(ActionQueryCriteria criteria) {
        ActionQueryCriteria actual = criteria != null ? criteria : new ActionQueryCriteria();
        int page = Math.max(1, actual.getPage());
        int size = Math.max(1, Math.min(actual.getSize(), 200));
        LambdaQueryWrapper<ActionExecution> wrapper = new LambdaQueryWrapper<ActionExecution>()
                .eq(hasText(actual.getTenantId()), ActionExecution::getTenantId, actual.getTenantId())
                .eq(hasText(actual.getActionType()), ActionExecution::getActionType, actual.getActionType())
                .eq(hasText(actual.getActorId()), ActionExecution::getActorId, actual.getActorId())
                .eq(hasText(actual.getActorType()), ActionExecution::getActorType, actual.getActorType())
                .eq(hasText(actual.getSource()), ActionExecution::getSource, actual.getSource())
                .eq(hasText(actual.getTargetType()), ActionExecution::getTargetType, actual.getTargetType())
                .eq(hasText(actual.getTargetId()), ActionExecution::getTargetId, actual.getTargetId())
                .eq(hasText(actual.getStatus()), ActionExecution::getStatus, actual.getStatus())
                .eq(hasText(actual.getTraceId()), ActionExecution::getTraceId, actual.getTraceId())
                .eq(hasText(actual.getCorrelationId()), ActionExecution::getCorrelationId, actual.getCorrelationId())
                .eq(hasText(actual.getIdempotencyKey()), ActionExecution::getIdempotencyKey, actual.getIdempotencyKey())
                .orderByDesc(ActionExecution::getUpdatedAt);
        IPage<ActionExecution> result = actionExecutionMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    private ActionExecution toExecution(GlobalActionRequest request,
                                        String tenantId,
                                        String actionType,
                                        String idempotencyKey,
                                        String payloadSummary) {
        ActionActor actor = request.getActor();
        ActionTarget target = request.getTarget();
        ActionContext context = request.getContext();

        ActionExecution execution = new ActionExecution();
        execution.setId(ActionCorrelationIds.firstNonBlank(request.getActionId(), ActionCorrelationIds.newActionId()));
        execution.setTenantId(tenantId);
        execution.setActionType(actionType);
        execution.setSource(enumName(request.getSource(), ActionSource.API));
        execution.setActorType(actor != null && actor.getType() != null ? actor.getType().name() : null);
        execution.setActorId(actor != null ? trimToNull(actor.getId()) : null);
        execution.setActorName(actor != null ? trimToNull(actor.getDisplayName()) : null);
        execution.setTargetType(target != null ? trimToNull(target.getType()) : null);
        execution.setTargetId(target != null ? trimToNull(target.getId()) : null);
        execution.setTargetOwnerService(target != null ? trimToNull(target.getOwnerService()) : null);
        execution.setTargetTenantId(target != null ? trimToNull(target.getTenantId()) : null);
        execution.setTargetVersion(target != null ? trimToNull(target.getVersion()) : null);
        execution.setStatus(ActionStatus.CREATED.name());
        execution.setExecutionMode(enumName(request.getExecutionMode(), ActionExecutionMode.SYNC));
        execution.setAuditLevel(ActionAuditLevel.NORMAL.name());
        execution.setIdempotencyKey(idempotencyKey);
        execution.setCorrelationId(context != null ? trimToNull(context.getCorrelationId()) : null);
        execution.setRequestId(context != null ? trimToNull(context.getRequestId()) : null);
        execution.setTraceId(context != null ? trimToNull(context.getTraceId()) : null);
        execution.setOwnerService(target != null ? trimToNull(target.getOwnerService()) : null);
        execution.setPayloadSummary(payloadSummary);
        execution.setRetryable(false);
        return execution;
    }

    private String resolveTenantId(GlobalActionRequest request) {
        if (request == null) {
            return GLOBAL_TENANT;
        }
        ActionContext context = request.getContext();
        ActionTarget target = request.getTarget();
        ActionActor actor = request.getActor();
        return normalizeTenant(firstNonBlank(
                context != null ? context.getTenantId() : null,
                target != null ? target.getTenantId() : null,
                actor != null ? actor.getTenantId() : null));
    }

    private String normalizeTenant(String tenantId) {
        String normalized = trimToNull(tenantId);
        return normalized != null ? normalized : GLOBAL_TENANT;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <E extends Enum<E>> String enumName(E actual, E fallback) {
        return actual != null ? actual.name() : fallback.name();
    }

    public record CreateResult(ActionExecution execution, boolean created) {
    }
}
