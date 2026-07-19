package com.triobase.common.action.definition;

import com.triobase.common.action.enums.ActionAuditLevel;
import lombok.Data;

@Data
public class ActionConfirmation {
    private boolean required;
    private String title;
    private String message;
    private String confirmLabel;
    private ActionAuditLevel riskLevel;
}
