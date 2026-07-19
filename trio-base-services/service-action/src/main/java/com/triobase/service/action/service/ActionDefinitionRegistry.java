package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.util.ActionTypeValidator;
import com.triobase.service.action.exception.ActionRuntimeException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ActionDefinitionRegistry {

    private static final int ERROR_UNKNOWN_ACTION = 40441;
    private static final int ERROR_DUPLICATE_ACTION = 40942;

    private final Map<String, ActionDefinition> definitions = new LinkedHashMap<>();

    public ActionDefinitionRegistry(List<ActionDefinitionProvider> providers) {
        if (providers != null) {
            providers.stream()
                    .map(ActionDefinitionProvider::definitions)
                    .forEach(this::registerAll);
        }
    }

    public synchronized void register(ActionDefinition definition) {
        String actionType = validateDefinition(definition);
        if (definitions.containsKey(actionType)) {
            throw new ActionRuntimeException(
                    ERROR_DUPLICATE_ACTION,
                    ActionErrorCategory.VALIDATION,
                    "ACTION_DEFINITION_DUPLICATED",
                    "actionType",
                    null);
        }
        definitions.put(actionType, definition);
    }

    public synchronized void registerOrReplace(ActionDefinition definition) {
        String actionType = validateDefinition(definition);
        definitions.put(actionType, definition);
    }

    public synchronized void registerAll(Collection<ActionDefinition> actionDefinitions) {
        if (actionDefinitions == null || actionDefinitions.isEmpty()) {
            return;
        }
        actionDefinitions.forEach(this::register);
    }

    public Optional<ActionDefinition> find(String actionType) {
        if (actionType == null || actionType.isBlank() || !ActionTypeValidator.valid(actionType)) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(actionType.trim()));
    }

    public ActionDefinition require(String actionType) {
        return find(actionType).orElseThrow(() -> new ActionRuntimeException(
                ERROR_UNKNOWN_ACTION,
                ActionErrorCategory.VALIDATION,
                "ACTION_DEFINITION_NOT_REGISTERED",
                "actionType",
                null));
    }

    public List<ActionDefinition> all() {
        return List.copyOf(definitions.values());
    }

    private String validateDefinition(ActionDefinition definition) {
        if (definition == null) {
            throw new ActionRuntimeException(
                    40043,
                    ActionErrorCategory.VALIDATION,
                    "ACTION_DEFINITION_REQUIRED");
        }
        String actionType = ActionTypeValidator.requireValid(definition.getActionType());
        if (definition.getOwnerService() == null || definition.getOwnerService().isBlank()) {
            throw new ActionRuntimeException(
                    40044,
                    ActionErrorCategory.VALIDATION,
                    "ACTION_DEFINITION_OWNER_REQUIRED",
                    "ownerService",
                    null);
        }
        return actionType;
    }
}
