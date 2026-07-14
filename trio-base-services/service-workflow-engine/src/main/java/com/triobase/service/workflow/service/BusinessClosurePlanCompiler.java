package com.triobase.service.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.BusinessObjectCatalogResponse;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusinessClosurePlanCompiler {

    private final BusinessObjectCatalogService catalogService;
    private final ObjectMapper objectMapper;

    public CompiledBusinessClosurePlan compile(ProcessPackageDefinition definition) {
        if (!hasBusinessClosureConfiguration(definition)) {
            return CompiledBusinessClosurePlan.empty();
        }

        String businessType = resolveBusinessType(definition);
        if (!StringUtils.hasText(businessType)) {
            throw new BizException(40000, "BUSINESS_OBJECT_BINDING_REQUIRED");
        }

        BusinessObjectCatalogResponse catalog = catalogService.getPublishedDetail(businessType.trim());
        CatalogIndex index = new CatalogIndex(catalog);
        return new CompiledBusinessClosurePlan(
                writeJson(bindingSnapshot(definition, catalog)),
                writeNullableJson(launchPlan(definition.getLaunchPolicy(), index)),
                writeNullableJson(permissionPlan(definition.getPermissionPolicy(), index)),
                writeNullableJson(closurePlan(definition.getClosurePolicy(), index)),
                writeNullableJson(agentFollowUpPlan(definition.getAgentFollowUpPolicy(), index)));
    }

    private boolean hasBusinessClosureConfiguration(ProcessPackageDefinition definition) {
        return definition.getBusinessObject() != null
                || definition.getLaunchPolicy() != null
                || definition.getPermissionPolicy() != null
                || definition.getClosurePolicy() != null
                || definition.getAgentFollowUpPolicy() != null;
    }

    private String resolveBusinessType(ProcessPackageDefinition definition) {
        if (definition.getBusinessObject() != null
                && StringUtils.hasText(definition.getBusinessObject().getTypeCode())) {
            return definition.getBusinessObject().getTypeCode();
        }
        if (definition.getLaunchPolicy() != null
                && StringUtils.hasText(definition.getLaunchPolicy().getBusinessObjectType())) {
            return definition.getLaunchPolicy().getBusinessObjectType();
        }
        if (definition.getClosurePolicy() != null
                && StringUtils.hasText(definition.getClosurePolicy().getBusinessObjectType())) {
            return definition.getClosurePolicy().getBusinessObjectType();
        }
        return null;
    }

    private Map<String, Object> bindingSnapshot(ProcessPackageDefinition definition,
                                                BusinessObjectCatalogResponse catalog) {
        Map<String, Object> snapshot = orderedMap();
        snapshot.put("businessObject", catalog.getObject());
        snapshot.put("businessRef", definition.getBusinessObject() == null
                ? null
                : definition.getBusinessObject().getBusinessRef());
        snapshot.put("statuses", catalog.getStatuses());
        snapshot.put("forms", catalog.getForms());
        snapshot.put("permissions", catalog.getPermissions());
        return snapshot;
    }

    private Map<String, Object> launchPlan(ProcessPackageDefinition.LaunchPolicy policy,
                                           CatalogIndex index) {
        if (policy == null) {
            return null;
        }
        Map<String, Object> plan = orderedMap();
        plan.put("policy", policy);
        plan.put("submitPermission", index.permissions().get(policy.getSubmitActionCode()));
        plan.put("createAction", actionSnapshot(index.actions().get(policy.getCreateActionCode())));
        plan.put("allowedStatuses", selectedStatuses(policy.getAllowedStatuses(), index));
        plan.put("startEffects", compileEffects(policy.getStartEffects(), index));
        return plan;
    }

    private Map<String, Object> permissionPlan(ProcessPackageDefinition.BusinessPermissionPolicy policy,
                                               CatalogIndex index) {
        if (policy == null) {
            return null;
        }
        Map<String, Object> plan = orderedMap();
        plan.put("policy", policy);
        Map<String, Object> resolved = orderedMap();
        putIfPresent(resolved, "submit", index.permissions().get(policy.getSubmitActionCode()));
        putIfPresent(resolved, "view", index.permissions().get(policy.getViewActionCode()));
        putIfPresent(resolved, "approve", index.permissions().get(policy.getApproveActionCode()));
        putIfPresent(resolved, "retryClosure", index.permissions().get(policy.getRetryClosureActionCode()));
        putIfPresent(resolved, "agentFollowUp", index.permissions().get(policy.getAgentFollowUpActionCode()));
        if (policy.getTaskActionCodes() != null) {
            Map<String, Object> taskActions = orderedMap();
            policy.getTaskActionCodes().forEach((key, value) ->
                    putIfPresent(taskActions, key, index.permissions().get(value)));
            resolved.put("taskActions", taskActions);
        }
        plan.put("resolvedPermissions", resolved);
        return plan;
    }

    private Map<String, Object> closurePlan(ProcessPackageDefinition.ClosurePolicy policy,
                                            CatalogIndex index) {
        if (policy == null) {
            return null;
        }
        Map<String, Object> plan = orderedMap();
        plan.put("businessRef", policy.getBusinessRef());
        Map<String, Object> outcomes = orderedMap();
        if (policy.getOutcomes() != null) {
            policy.getOutcomes().forEach((outcome, effects) ->
                    outcomes.put(outcome, compileEffects(effects, index)));
        }
        plan.put("outcomes", outcomes);
        plan.put("failureEffects", compileEffects(policy.getFailureEffects(), index));
        return plan;
    }

    private Map<String, Object> agentFollowUpPlan(ProcessPackageDefinition.AgentFollowUpPolicy policy,
                                                  CatalogIndex index) {
        if (policy == null) {
            return null;
        }
        Map<String, Object> plan = orderedMap();
        plan.put("actions", compileEffects(policy.getActions(), index));
        return plan;
    }

    private List<?> compileEffects(List<ProcessPackageDefinition.ClosureEffectDefinition> effects,
                                   CatalogIndex index) {
        if (CollectionUtils.isEmpty(effects)) {
            return List.of();
        }
        return effects.stream().map(effect -> compileEffect(effect, index)).toList();
    }

    private Map<String, Object> compileEffect(ProcessPackageDefinition.ClosureEffectDefinition effect,
                                              CatalogIndex index) {
        Map<String, Object> compiled = orderedMap();
        compiled.put("effectKey", effect.getEffectKey());
        compiled.put("mode", effect.getMode());
        compiled.put("params", effect.getParams());
        if (StringUtils.hasText(effect.getActionCode())) {
            compiled.put("selectorType", "ACTION");
            compiled.put("action", actionSnapshot(index.actions().get(effect.getActionCode())));
        } else if (StringUtils.hasText(effect.getAgentActionCode())) {
            compiled.put("selectorType", "AGENT_ACTION");
            compiled.put("agentAction", agentActionSnapshot(index.agentActions().get(effect.getAgentActionCode())));
        } else if (StringUtils.hasText(effect.getEventCode())) {
            compiled.put("selectorType", "EVENT");
            compiled.put("event", index.events().get(effect.getEventCode()));
        }
        return compiled;
    }

    private List<?> selectedStatuses(List<String> statusCodes, CatalogIndex index) {
        if (statusCodes == null) {
            return List.of();
        }
        return statusCodes.stream()
                .filter(StringUtils::hasText)
                .map(code -> index.statuses().get(code))
                .toList();
    }

    private Map<String, Object> actionSnapshot(BusinessObjectCatalogResponse.ActionItem action) {
        if (action == null) {
            return null;
        }
        Map<String, Object> snapshot = orderedMap();
        snapshot.put("actionCode", action.getActionCode());
        snapshot.put("displayName", action.getDisplayName());
        snapshot.put("actionType", action.getActionType());
        snapshot.put("executorKey", action.getExecutorKey());
        snapshot.put("modeDefault", action.getModeDefault());
        snapshot.put("permissionAction", action.getPermissionAction());
        snapshot.put("paramSchemaJson", action.getParamSchemaJson());
        return snapshot;
    }

    private Map<String, Object> agentActionSnapshot(BusinessObjectCatalogResponse.AgentActionItem action) {
        if (action == null) {
            return null;
        }
        Map<String, Object> snapshot = orderedMap();
        snapshot.put("agentActionCode", action.getAgentActionCode());
        snapshot.put("displayName", action.getDisplayName());
        snapshot.put("executorKey", action.getExecutorKey());
        snapshot.put("permissionAction", action.getPermissionAction());
        snapshot.put("paramSchemaJson", action.getParamSchemaJson());
        snapshot.put("resultSchemaJson", action.getResultSchemaJson());
        snapshot.put("modeDefault", action.getModeDefault());
        return snapshot;
    }

    private Map<String, Object> orderedMap() {
        return new LinkedHashMap<>();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String writeNullableJson(Object value) {
        return value == null ? null : writeJson(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(50000, "BUSINESS_CLOSURE_PLAN_SERIALIZATION_FAILED");
        }
    }

    public record CompiledBusinessClosurePlan(
            String businessBindingSnapshot,
            String launchPlanJson,
            String permissionPlanJson,
            String closurePlanJson,
            String agentFollowUpPlanJson) {

        public static CompiledBusinessClosurePlan empty() {
            return new CompiledBusinessClosurePlan(null, null, null, null, null);
        }
    }

    private record CatalogIndex(
            Map<String, BusinessObjectCatalogResponse.StatusItem> statuses,
            Map<String, BusinessObjectCatalogResponse.PermissionItem> permissions,
            Map<String, BusinessObjectCatalogResponse.ActionItem> actions,
            Map<String, BusinessObjectCatalogResponse.EventItem> events,
            Map<String, BusinessObjectCatalogResponse.AgentActionItem> agentActions) {

        private CatalogIndex(BusinessObjectCatalogResponse catalog) {
            this(index(catalog.getStatuses(), BusinessObjectCatalogResponse.StatusItem::getStatusCode),
                    index(catalog.getPermissions(), BusinessObjectCatalogResponse.PermissionItem::getActionCode),
                    index(catalog.getActions(), BusinessObjectCatalogResponse.ActionItem::getActionCode),
                    index(catalog.getEvents(), BusinessObjectCatalogResponse.EventItem::getEventCode),
                    index(catalog.getAgentActions(), BusinessObjectCatalogResponse.AgentActionItem::getAgentActionCode));
        }

        private static <T> Map<String, T> index(List<T> items, Function<T, String> keyFn) {
            if (items == null) {
                return Map.of();
            }
            return items.stream()
                    .filter(item -> StringUtils.hasText(keyFn.apply(item)))
                    .collect(Collectors.toMap(
                            item -> keyFn.apply(item).trim(),
                            Function.identity(),
                            (left, right) -> right,
                            LinkedHashMap::new));
        }
    }
}
