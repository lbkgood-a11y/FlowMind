package com.triobase.service.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.workflow.dto.StartProcessRequest;
import com.triobase.service.workflow.entity.ExpenseReportFixture;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.executor.BusinessActionContext;
import com.triobase.service.workflow.executor.BusinessActionExecutor;
import com.triobase.service.workflow.executor.BusinessActionResult;
import com.triobase.service.workflow.executor.ProcessExecutorRegistry;
import com.triobase.service.workflow.mapper.ExpenseReportFixtureMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BusinessLaunchRuntimeService {

    private static final String EXISTING_DOCUMENT = "EXISTING_DOCUMENT";
    private static final String CREATE_AND_LAUNCH = "CREATE_AND_LAUNCH";
    private static final String EXPENSE_REPORT = "expense_report";

    private final ObjectMapper objectMapper;
    private final ProcessExecutorRegistry executorRegistry;
    private final ExpenseReportFixtureMapper expenseReportFixtureMapper;

    public BusinessLaunchResult prepareLaunch(ProcessPackage pkg,
                                              StartProcessRequest request,
                                              String operatorId) {
        if (!StringUtils.hasText(pkg.getBusinessBindingSnapshot())
                || !StringUtils.hasText(pkg.getLaunchPlanJson())) {
            return BusinessLaunchResult.empty();
        }

        JsonNode binding = readTree(pkg.getBusinessBindingSnapshot(), "INVALID_BUSINESS_BINDING_SNAPSHOT");
        JsonNode launchPlan = readTree(pkg.getLaunchPlanJson(), "INVALID_LAUNCH_PLAN");
        String businessType = firstText(
                request.getBusinessType(),
                binding.path("businessObject").path("typeCode").asText(null));
        if (!StringUtils.hasText(businessType)) {
            throw new BizException(40000, "BUSINESS_OBJECT_BINDING_REQUIRED");
        }

        requireSubmitPermission(launchPlan);

        String launchMode = resolveLaunchMode(request, launchPlan);
        if (CREATE_AND_LAUNCH.equals(launchMode)) {
            requireIdempotencyKey(request);
            String businessId = createBusinessDocument(businessType, launchPlan, request, operatorId);
            executeStartEffects(businessType, businessId, launchPlan, request, operatorId);
            return new BusinessLaunchResult(businessType, businessId, launchMode);
        }

        String businessId = firstText(request.getBusinessId(), readBusinessRefFromForm(request));
        if (!StringUtils.hasText(businessId)) {
            throw new BizException(40000, "BUSINESS_ID_REQUIRED");
        }
        String currentStatus = loadBusinessStatus(businessType, businessId);
        if (!allowedStatuses(launchPlan).isEmpty()
                && !allowedStatuses(launchPlan).contains(currentStatus)) {
            throw new BizException(40000, "BUSINESS_DOCUMENT_STATUS_NOT_ALLOWED");
        }
        executeStartEffects(businessType, businessId, launchPlan, request, operatorId);
        return new BusinessLaunchResult(businessType, businessId, launchMode);
    }

    private void requireSubmitPermission(JsonNode launchPlan) {
        String permissionCode = launchPlan.path("submitPermission").path("permissionCode").asText(null);
        if (StringUtils.hasText(permissionCode)
                && !SecurityContextHolder.getPermissions().contains(permissionCode)) {
            throw new BizException(40300, "BUSINESS_SUBMIT_PERMISSION_DENIED");
        }
    }

    private String resolveLaunchMode(StartProcessRequest request, JsonNode launchPlan) {
        String launchMode = firstText(request.getLaunchMode(),
                StringUtils.hasText(request.getBusinessId()) ? EXISTING_DOCUMENT : CREATE_AND_LAUNCH);
        launchMode = launchMode.trim().toUpperCase();
        Set<String> modes = values(launchPlan.path("policy").path("modes"));
        if (!modes.isEmpty() && !modes.contains(launchMode)) {
            throw new BizException(40000, "LAUNCH_MODE_NOT_ALLOWED");
        }
        return launchMode;
    }

    private void requireIdempotencyKey(StartProcessRequest request) {
        if (!StringUtils.hasText(request.getIdempotencyKey())) {
            throw new BizException(40000, "LAUNCH_IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    private String createBusinessDocument(String businessType,
                                          JsonNode launchPlan,
                                          StartProcessRequest request,
                                          String operatorId) {
        JsonNode action = launchPlan.path("createAction");
        String executorKey = action.path("executorKey").asText(null);
        BusinessActionExecutor executor = executorRegistry.businessActionExecutor(executorKey);
        if (executor == null) {
            throw new BizException(40000, "BUSINESS_CREATE_EXECUTOR_NOT_REGISTERED");
        }
        BusinessActionResult result = executor.execute(new BusinessActionContext(
                SecurityContextHolder.getTenantId(),
                businessType,
                null,
                action.path("actionCode").asText(null),
                request.getFormData(),
                request.getIdempotencyKey().trim() + ":create",
                TraceUtil.getTraceId(),
                operatorId));
        if (!result.success()) {
            throw new BizException(40000, "BUSINESS_DOCUMENT_CREATE_FAILED:" + result.resultCode());
        }
        return result.businessId();
    }

    private void executeStartEffects(String businessType,
                                     String businessId,
                                     JsonNode launchPlan,
                                     StartProcessRequest request,
                                     String operatorId) {
        JsonNode effects = launchPlan.path("startEffects");
        if (!effects.isArray()) {
            return;
        }
        int index = 0;
        for (JsonNode effect : effects) {
            JsonNode action = effect.path("action");
            String executorKey = action.path("executorKey").asText(null);
            BusinessActionExecutor executor = executorRegistry.businessActionExecutor(executorKey);
            if (executor == null) {
                throw new BizException(40000, "START_EFFECT_EXECUTOR_NOT_REGISTERED");
            }
            BusinessActionResult result = executor.execute(new BusinessActionContext(
                    SecurityContextHolder.getTenantId(),
                    businessType,
                    businessId,
                    action.path("actionCode").asText(null),
                    params(effect),
                    startEffectIdempotencyKey(request, action, index),
                    TraceUtil.getTraceId(),
                    operatorId));
            if (!result.success()) {
                throw new BizException(40000, "START_EFFECT_FAILED:" + result.resultCode());
            }
            index++;
        }
    }

    private String startEffectIdempotencyKey(StartProcessRequest request, JsonNode action, int index) {
        String root = StringUtils.hasText(request.getIdempotencyKey())
                ? request.getIdempotencyKey().trim()
                : "existing:" + request.getProcessKey() + ":" + request.getBusinessId();
        return root + ":start:" + index + ":" + action.path("actionCode").asText("action");
    }

    private Map<String, Object> params(JsonNode effect) {
        JsonNode params = effect.path("params");
        if (params == null || params.isMissingNode() || params.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(params, new TypeReference<>() {
        });
    }

    private String loadBusinessStatus(String businessType, String businessId) {
        if (!EXPENSE_REPORT.equals(businessType)) {
            throw new BizException(40000, "BUSINESS_OBJECT_RUNTIME_NOT_SUPPORTED");
        }
        ExpenseReportFixture report = expenseReportFixtureMapper.selectById(businessId);
        if (report == null) {
            throw new BizException(40400, "BUSINESS_DOCUMENT_NOT_FOUND");
        }
        return report.getStatus();
    }

    private String readBusinessRefFromForm(StartProcessRequest request) {
        Object value = request.getFormData() == null ? null : request.getFormData().get("businessId");
        return value == null ? null : String.valueOf(value);
    }

    private Set<String> allowedStatuses(JsonNode launchPlan) {
        return values(launchPlan.path("allowedStatuses"));
    }

    private Set<String> values(JsonNode array) {
        Set<String> values = new LinkedHashSet<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                String value = node.isTextual()
                        ? node.asText()
                        : firstText(node.path("statusCode").asText(null), node.path("mode").asText(null));
                if (StringUtils.hasText(value)) {
                    values.add(value.trim().toUpperCase());
                }
            }
        }
        return values;
    }

    private JsonNode readTree(String json, String errorCode) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BizException(50000, errorCode);
        }
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    public record BusinessLaunchResult(String businessType, String businessId, String launchMode) {

        public static BusinessLaunchResult empty() {
            return new BusinessLaunchResult(null, null, null);
        }
    }
}
