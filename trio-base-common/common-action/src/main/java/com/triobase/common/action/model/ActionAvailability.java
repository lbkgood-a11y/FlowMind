package com.triobase.common.action.model;

import com.triobase.common.action.definition.ActionConfirmation;
import com.triobase.common.action.enums.ActionExecutionMode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionAvailability {
    private String actionType;
    private boolean visible = true;
    private boolean enabled = true;
    private String disabledReason;
    private boolean danger;
    private boolean requiresConfirmation;
    private ActionConfirmation confirmation;
    private ActionExecutionMode executionMode;
    private String targetStatus;
    private String targetStatusGroup;
    private List<String> refreshScopes = new ArrayList<>();
    private List<ActionError> errors = new ArrayList<>();

    public static ActionAvailability enabled(String actionType) {
        ActionAvailability availability = new ActionAvailability();
        availability.setActionType(actionType);
        availability.setVisible(true);
        availability.setEnabled(true);
        return availability;
    }

    public static ActionAvailability disabled(String actionType, String reason) {
        ActionAvailability availability = enabled(actionType);
        availability.setEnabled(false);
        availability.setDisabledReason(reason);
        return availability;
    }
}
