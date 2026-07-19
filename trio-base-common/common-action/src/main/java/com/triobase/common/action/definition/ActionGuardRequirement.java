package com.triobase.common.action.definition;

import lombok.Data;

@Data
public class ActionGuardRequirement {
    private String guardCode;
    private String ownerService;
    private String description;
    private String configSchemaJson;
    private boolean mandatory = true;
}
