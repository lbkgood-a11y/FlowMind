package com.triobase.service.workflow.executor.expense;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.workflow.config.WorkflowIntegrationProperties;
import com.triobase.service.workflow.entity.ExpenseReportActionLog;
import com.triobase.service.workflow.executor.BusinessActionContext;
import com.triobase.service.workflow.executor.BusinessActionResult;
import com.triobase.service.workflow.mapper.ExpenseReportActionLogMapper;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

abstract class ExpenseReportExecutorSupport {

    private static final String GLOBAL_TENANT = "GLOBAL";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";

    private final ObjectMapper objectMapper;
    private final ExpenseReportActionLogMapper actionLogMapper;
    private final WorkflowIntegrationProperties properties;

    protected ExpenseReportExecutorSupport(ObjectMapper objectMapper,
                                           ExpenseReportActionLogMapper actionLogMapper,
                                           WorkflowIntegrationProperties properties) {
        this.objectMapper = objectMapper;
        this.actionLogMapper = actionLogMapper;
        this.properties = properties;
    }

    protected BusinessActionResult executeOnce(String executorKey,
                                               String actionCode,
                                               BusinessActionContext context,
                                               Function<ExpenseReportActionLog, BusinessActionResult> work) {
        if (context == null || !StringUtils.hasText(context.idempotencyKey())) {
            return BusinessActionResult.failed("IDEMPOTENCY_KEY_REQUIRED",
                    "Expense report executor requires an idempotency key");
        }

        ExpenseReportActionLog existing = findLog(executorKey, context.idempotencyKey());
        if (existing != null) {
            return replay(existing);
        }

        ExpenseReportActionLog log = new ExpenseReportActionLog();
        log.setId(UlidGenerator.nextUlid());
        log.setTenantId(tenantId(context));
        log.setExecutorKey(executorKey);
        log.setIdempotencyKey(context.idempotencyKey().trim());
        log.setBusinessId(trimToNull(context.businessId()));
        log.setActionCode(actionCode);
        log.setStatus(STATUS_PENDING);
        log.setRequestJson(writeJson(parameters(context)));
        log.setTraceId(trimToNull(context.traceId()));
        actionLogMapper.insert(log);

        long startedAt = System.nanoTime();
        BusinessActionResult result;
        try {
            result = work.apply(log);
        } catch (RuntimeException ex) {
            result = BusinessActionResult.failed("EXPENSE_EXECUTOR_ERROR", ex.getMessage());
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        if (elapsed.compareTo(properties.getBusinessClosure().getExecutorTimeout()) > 0) {
            result = BusinessActionResult.failed("EXPENSE_EXECUTOR_TIMEOUT",
                    "Expense report executor exceeded timeout "
                            + properties.getBusinessClosure().getExecutorTimeout().toMillis() + "ms");
        }

        if (result.success()) {
            recordSuccess(log, result);
        } else {
            recordFailure(log, result);
        }
        return result;
    }

    protected Map<String, Object> parameters(BusinessActionContext context) {
        return context.parameters() == null ? Map.of() : context.parameters();
    }

    protected String tenantId(BusinessActionContext context) {
        return StringUtils.hasText(context.tenantId()) ? context.tenantId().trim() : GLOBAL_TENANT;
    }

    protected String stringParameter(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    protected BigDecimal decimalParameter(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return new BigDecimal(text.trim());
        }
        return null;
    }

    protected Map<String, Object> resultData() {
        return new LinkedHashMap<>();
    }

    protected String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    protected String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"serialization\":\"failed\"}";
        }
    }

    private ExpenseReportActionLog findLog(String executorKey, String idempotencyKey) {
        return actionLogMapper.selectOne(
                new LambdaQueryWrapper<ExpenseReportActionLog>()
                        .eq(ExpenseReportActionLog::getExecutorKey, executorKey)
                        .eq(ExpenseReportActionLog::getIdempotencyKey, idempotencyKey.trim()));
    }

    private BusinessActionResult replay(ExpenseReportActionLog log) {
        if (STATUS_SUCCEEDED.equals(log.getStatus())) {
            Map<String, Object> data = resultData();
            data.put("replayed", true);
            data.put("traceId", log.getTraceId());
            data.put("result", log.getResultJson());
            return new BusinessActionResult(true, log.getResultCode(),
                    "IDEMPOTENT_REPLAY", log.getBusinessId(), data);
        }
        if (STATUS_FAILED.equals(log.getStatus())) {
            return BusinessActionResult.failed(log.getResultCode(), log.getLastError());
        }
        return BusinessActionResult.failed("EXPENSE_ACTION_IN_PROGRESS",
                "Expense report action is already running for this idempotency key");
    }

    private void recordSuccess(ExpenseReportActionLog log, BusinessActionResult result) {
        log.setStatus(STATUS_SUCCEEDED);
        log.setResultCode(result.resultCode());
        log.setBusinessId(result.businessId());
        log.setResultJson(writeJson(result.data()));
        log.setLastError(null);
        actionLogMapper.updateById(log);
    }

    private void recordFailure(ExpenseReportActionLog log, BusinessActionResult result) {
        log.setStatus(STATUS_FAILED);
        log.setResultCode(result.resultCode());
        log.setLastError(result.message());
        log.setResultJson(writeJson(result.data()));
        actionLogMapper.updateById(log);
    }
}
