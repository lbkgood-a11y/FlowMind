package com.triobase.common.action.model;

import com.triobase.common.action.definition.ActionConfirmation;
import com.triobase.common.action.enums.ActionExecutionMode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionCandidateValidationResult {
    private String candidateId;
    private String actionType;
    private boolean definitionExists;
    private boolean schemaValid;
    private boolean requiresConfirmation;
    private boolean confirmationSatisfied;
    private boolean valid;
    private boolean dispatchable;
    private boolean visible = true;
    private boolean enabled = true;
    private String disabledReason;
    private boolean danger;
    private ActionExecutionMode executionMode;
    private String targetStatus;
    private String targetStatusGroup;
    private ActionConfirmation confirmation;
    private GlobalActionRequest actionRequest;
    private List<String> refreshScopes = new ArrayList<>();
    private List<ActionError> errors = new ArrayList<>();

    public ActionAvailability toAvailability() {
        ActionAvailability availability = new ActionAvailability();
        availability.setActionType(actionType);
        availability.setVisible(visible);
        availability.setEnabled(enabled);
        availability.setDisabledReason(disabledReason);
        availability.setDanger(danger);
        availability.setRequiresConfirmation(requiresConfirmation);
        availability.setConfirmation(confirmation);
        availability.setExecutionMode(executionMode);
        availability.setTargetStatus(targetStatus);
        availability.setTargetStatusGroup(targetStatusGroup);
        availability.setRefreshScopes(refreshScopes != null ? refreshScopes : List.of());
        availability.setErrors(errors != null ? errors : List.of());
        return availability;
    }
}
