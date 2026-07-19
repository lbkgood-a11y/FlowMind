package com.triobase.service.action.dto;

import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionErrorResponse {
    private String actionId;
    private ActionStatus status;
    private String traceId;
    private List<ActionError> errors = new ArrayList<>();

    public static ActionErrorResponse rejected(ActionError error, String traceId) {
        ActionErrorResponse response = new ActionErrorResponse();
        response.setStatus(ActionStatus.REJECTED);
        response.setTraceId(traceId);
        response.getErrors().add(error);
        return response;
    }
}
