package com.triobase.common.action.definition;

import lombok.Data;

@Data
public class ActionGuardRequirement {
    private String guardCode;
    private String ownerService;
    private String description;
    private String configSchemaJson;
    private boolean mandatory = true;

    public static ActionGuardRequirement of(String ownerService, String code, String description) {
        ActionGuardRequirement guard = new ActionGuardRequirement();
        guard.setGuardCode(code);
        guard.setOwnerService(ownerService);
        guard.setDescription(description);
        return guard;
    }
}
