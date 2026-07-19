package com.triobase.common.action.owner;

import com.triobase.common.action.model.ActionError;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionOwnerGuardResponse {
    private boolean allowed;
    private String guardCode;
    private String message;
    private List<ActionError> errors = new ArrayList<>();

    public static ActionOwnerGuardResponse allowed(String guardCode) {
        ActionOwnerGuardResponse response = new ActionOwnerGuardResponse();
        response.setAllowed(true);
        response.setGuardCode(guardCode);
        return response;
    }

    public static ActionOwnerGuardResponse denied(String guardCode,
                                                  String message,
                                                  List<ActionError> errors) {
        ActionOwnerGuardResponse response = new ActionOwnerGuardResponse();
        response.setAllowed(false);
        response.setGuardCode(guardCode);
        response.setMessage(message);
        response.setErrors(errors != null ? errors : List.of());
        return response;
    }
}
