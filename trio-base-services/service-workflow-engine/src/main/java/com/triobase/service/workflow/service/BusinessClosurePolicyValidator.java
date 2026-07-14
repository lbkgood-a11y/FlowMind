package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.BusinessObjectCatalogResponse;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.executor.ProcessExecutorRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusinessClosurePolicyValidator {

    private final BusinessObjectCatalogService catalogService;
    private final ProcessExecutorRegistry executorRegistry;
    private final ObjectMapper objectMapper;

    public void validate(ProcessPackageDefinition definition) {
        if (!hasBusinessClosureConfiguration(definition)) {
            return;
        }

        String businessType = resolveBusinessType(definition);
        if (!StringUtils.hasText(businessType)) {
            throw new BizException(40000, "BUSINESS_OBJECT_BINDING_REQUIRED");
        }

        BusinessObjectCatalogResponse catalog = catalogService.getPublishedDetail(businessType.trim());
        CatalogIndex index = new CatalogIndex(catalog);
        validateBusinessRefSources(definition);
        validateLaunchPolicy(definition.getLaunchPolicy(), index);
        validatePermissionPolicy(definition.getPermissionPolicy(), index);
        validateClosurePolicy(definition.getClosurePolicy(), index);
        validateAgentFollowUpPolicy(definition.getAgentFollowUpPolicy(), index);
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

    private void validateLaunchPolicy(ProcessPackageDefinition.LaunchPolicy policy,
                                      CatalogIndex index) {
        if (policy == null) {
            return;
        }
        requireKnownStatuses(policy.getAllowedStatuses(), index);
        requirePermission(policy.getSubmitActionCode(), index);
        requireActionExecutor(policy.getCreateActionCode(), index);
        validateEffects(policy.getStartEffects(), index);
    }

    private void validatePermissionPolicy(ProcessPackageDefinition.BusinessPermissionPolicy policy,
                                          CatalogIndex index) {
        if (policy == null) {
            return;
        }
        requirePermission(policy.getSubmitActionCode(), index);
        requirePermission(policy.getViewActionCode(), index);
        requirePermission(policy.getApproveActionCode(), index);
        requirePermission(policy.getRetryClosureActionCode(), index);
        requirePermission(policy.getAgentFollowUpActionCode(), index);
        if (policy.getTaskActionCodes() != null) {
            policy.getTaskActionCodes().values().forEach(code -> requirePermission(code, index));
        }
    }

    private void validateClosurePolicy(ProcessPackageDefinition.ClosurePolicy policy,
                                       CatalogIndex index) {
        if (policy == null) {
            return;
        }
        if (policy.getOutcomes() != null) {
            policy.getOutcomes().values().forEach(effects -> validateEffects(effects, index));
        }
        validateEffects(policy.getFailureEffects(), index);
    }

    private void validateAgentFollowUpPolicy(ProcessPackageDefinition.AgentFollowUpPolicy policy,
                                             CatalogIndex index) {
        if (policy == null) {
            return;
        }
        validateEffects(policy.getActions(), index);
    }

    private void validateEffects(List<ProcessPackageDefinition.ClosureEffectDefinition> effects,
                                 CatalogIndex index) {
        if (CollectionUtils.isEmpty(effects)) {
            return;
        }
        for (ProcessPackageDefinition.ClosureEffectDefinition effect : effects) {
            validateEffect(effect, index);
        }
    }

    private void validateEffect(ProcessPackageDefinition.ClosureEffectDefinition effect,
                                CatalogIndex index) {
        if (effect == null) {
            throw new BizException(40000, "BUSINESS_CLOSURE_EFFECT_REQUIRED");
        }
        rejectForbiddenExecution(effect);

        int selected = selectedCount(effect.getActionCode(),
                effect.getAgentActionCode(), effect.getEventCode());
        if (selected == 0) {
            throw new BizException(40000, "BUSINESS_CLOSURE_EFFECT_SELECTOR_REQUIRED");
        }
        if (selected > 1) {
            throw new BizException(40000, "BUSINESS_CLOSURE_EFFECT_SINGLE_SELECTOR_REQUIRED");
        }

        if (StringUtils.hasText(effect.getActionCode())) {
            BusinessObjectCatalogResponse.ActionItem action =
                    requireActionExecutor(effect.getActionCode(), index);
            validateActionStatusParams(action, effect, index);
            validateRequiredParams(action.getParamSchemaJson(), effect.getParams(),
                    "BUSINESS_ACTION_PARAM_REQUIRED");
        } else if (StringUtils.hasText(effect.getAgentActionCode())) {
            BusinessObjectCatalogResponse.AgentActionItem agentAction =
                    requireAgentExecutor(effect.getAgentActionCode(), index);
            requireAgentPermission(agentAction, index);
            validateRequiredParams(agentAction.getParamSchemaJson(), effect.getParams(),
                    "AGENT_ACTION_PARAM_REQUIRED");
        } else {
            requireEvent(effect.getEventCode(), index);
        }
    }

    private void rejectForbiddenExecution(ProcessPackageDefinition.ClosureEffectDefinition effect) {
        if (StringUtils.hasText(effect.getExecutorKey())
                || StringUtils.hasText(effect.getUrl())
                || StringUtils.hasText(effect.getSql())
                || StringUtils.hasText(effect.getScript())
                || StringUtils.hasText(effect.getClassName())
                || StringUtils.hasText(effect.getPrompt())
                || !CollectionUtils.isEmpty(effect.getConnector())
                || !CollectionUtils.isEmpty(effect.getToolCall())
                || !CollectionUtils.isEmpty(effect.getExecution())) {
            throw new BizException(40000, "BUSINESS_CLOSURE_FORBIDDEN_EXECUTION_DEFINITION");
        }
    }

    private void validateBusinessRefSources(ProcessPackageDefinition definition) {
        if (definition.getBusinessObject() != null) {
            validateBusinessRefSource(definition.getBusinessObject().getBusinessRef(), definition);
        }
        if (definition.getLaunchPolicy() != null) {
            validateBusinessRefSource(definition.getLaunchPolicy().getBusinessRef(), definition);
        }
        if (definition.getClosurePolicy() != null) {
            validateBusinessRefSource(definition.getClosurePolicy().getBusinessRef(), definition);
        }
    }

    private void validateBusinessRefSource(ProcessPackageDefinition.BusinessRefSource source,
                                           ProcessPackageDefinition definition) {
        if (source == null) {
            return;
        }
        if (!StringUtils.hasText(source.getSourceType())) {
            throw new BizException(40000, "BUSINESS_REF_SOURCE_TYPE_REQUIRED");
        }
        String sourceType = source.getSourceType().trim().toUpperCase(Locale.ROOT);
        source.setSourceType(sourceType);
        switch (sourceType) {
            case "FORM_FIELD" -> {
                if (!StringUtils.hasText(source.getFieldKey())) {
                    throw new BizException(40000, "BUSINESS_REF_FORM_FIELD_REQUIRED");
                }
                if (!formFieldKeys(definition).contains(source.getFieldKey().trim())) {
                    throw new BizException(40000, "BUSINESS_REF_FORM_FIELD_NOT_FOUND");
                }
            }
            case "PAGE_CONTEXT", "PROCESS_CONTEXT" -> {
                if (!StringUtils.hasText(source.getContextKey())) {
                    throw new BizException(40000, "BUSINESS_REF_CONTEXT_KEY_REQUIRED");
                }
            }
            case "API_INPUT" -> {
                // businessId is supplied by StartProcessRequest.
            }
            case "FIXED" -> {
                if (!StringUtils.hasText(source.getFixedValue())) {
                    throw new BizException(40000, "BUSINESS_REF_FIXED_VALUE_REQUIRED");
                }
            }
            default -> throw new BizException(40000, "BUSINESS_REF_SOURCE_TYPE_UNSUPPORTED");
        }
    }

    private Set<String> formFieldKeys(ProcessPackageDefinition definition) {
        if (definition.getForm() == null || definition.getForm().getSchema() == null) {
            return Set.of();
        }
        Object properties = definition.getForm().getSchema().get("properties");
        if (!(properties instanceof Map<?, ?> propertyMap)) {
            return Set.of();
        }
        return propertyMap.keySet().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toSet());
    }

    private int selectedCount(String actionCode, String agentActionCode, String eventCode) {
        int selected = 0;
        if (StringUtils.hasText(actionCode)) {
            selected++;
        }
        if (StringUtils.hasText(agentActionCode)) {
            selected++;
        }
        if (StringUtils.hasText(eventCode)) {
            selected++;
        }
        return selected;
    }

    private void requireKnownStatuses(List<String> statusCodes, CatalogIndex index) {
        if (statusCodes == null) {
            return;
        }
        for (String statusCode : statusCodes) {
            if (StringUtils.hasText(statusCode) && !index.statuses().containsKey(statusCode)) {
                throw new BizException(40000, "BUSINESS_STATUS_NOT_IN_CATALOG");
            }
        }
    }

    private void requirePermission(String actionCode, CatalogIndex index) {
        if (StringUtils.hasText(actionCode) && !index.permissions().containsKey(actionCode)) {
            throw new BizException(40000, "BUSINESS_PERMISSION_NOT_IN_CATALOG");
        }
    }

    private BusinessObjectCatalogResponse.ActionItem requireActionExecutor(String actionCode, CatalogIndex index) {
        if (!StringUtils.hasText(actionCode)) {
            return null;
        }
        BusinessObjectCatalogResponse.ActionItem action = index.actions().get(actionCode);
        if (action == null) {
            throw new BizException(40000, "BUSINESS_ACTION_NOT_IN_CATALOG");
        }
        if (!executorRegistry.hasBusinessOrClosureExecutor(action.getExecutorKey())) {
            throw new BizException(40000, "BUSINESS_ACTION_EXECUTOR_NOT_REGISTERED");
        }
        return action;
    }

    private BusinessObjectCatalogResponse.AgentActionItem requireAgentExecutor(String agentActionCode,
                                                                               CatalogIndex index) {
        BusinessObjectCatalogResponse.AgentActionItem agentAction = index.agentActions()
                .get(agentActionCode);
        if (agentAction == null) {
            throw new BizException(40000, "AGENT_ACTION_NOT_IN_CATALOG");
        }
        if (!executorRegistry.hasAgentFollowUpExecutor(agentAction.getExecutorKey())) {
            throw new BizException(40000, "AGENT_ACTION_EXECUTOR_NOT_REGISTERED");
        }
        return agentAction;
    }

    private void requireAgentPermission(BusinessObjectCatalogResponse.AgentActionItem agentAction,
                                        CatalogIndex index) {
        if (!StringUtils.hasText(agentAction.getPermissionAction())
                || !index.permissions().containsKey(agentAction.getPermissionAction())) {
            throw new BizException(40000, "AGENT_ACTION_PERMISSION_NOT_IN_CATALOG");
        }
    }

    private void requireEvent(String eventCode, CatalogIndex index) {
        if (StringUtils.hasText(eventCode) && !index.events().containsKey(eventCode)) {
            throw new BizException(40000, "BUSINESS_EVENT_NOT_IN_CATALOG");
        }
    }

    private void validateRequiredParams(String paramSchemaJson,
                                        Map<String, Object> params,
                                        String errorCode) {
        if (!StringUtils.hasText(paramSchemaJson)) {
            return;
        }
        JsonNode required;
        try {
            required = objectMapper.readTree(paramSchemaJson).path("required");
        } catch (Exception e) {
            throw new BizException(40000, "BUSINESS_CLOSURE_PARAM_SCHEMA_INVALID");
        }
        if (!required.isArray()) {
            return;
        }
        for (JsonNode item : required) {
            String key = item.asText(null);
            if (!StringUtils.hasText(key)
                    || params == null
                    || !params.containsKey(key)
                    || params.get(key) == null
                    || (params.get(key) instanceof String text && !StringUtils.hasText(text))) {
                throw new BizException(40000, errorCode);
            }
        }
    }

    private void validateActionStatusParams(BusinessObjectCatalogResponse.ActionItem action,
                                            ProcessPackageDefinition.ClosureEffectDefinition effect,
                                            CatalogIndex index) {
        if (action == null || !"UPDATE_STATUS".equals(action.getActionType())) {
            return;
        }
        Object statusValue = effect.getParams() == null ? null : effect.getParams().get("status");
        if (statusValue == null && effect.getParams() != null) {
            statusValue = effect.getParams().get("targetStatus");
        }
        if (!(statusValue instanceof String status) || !StringUtils.hasText(status)
                || !index.statuses().containsKey(status.trim())) {
            throw new BizException(40000, "BUSINESS_STATUS_NOT_IN_CATALOG");
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
                            (left, right) -> right));
        }
    }
}
