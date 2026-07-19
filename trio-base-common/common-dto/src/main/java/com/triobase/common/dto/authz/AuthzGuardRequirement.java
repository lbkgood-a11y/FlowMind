package com.triobase.common.dto.authz;

import lombok.Data;

@Data
public class AuthzGuardRequirement {
    private String guardCode;
    private String ownerService;
    private String description;
    private String configSchemaJson;
}
