package com.triobase.common.dto.authz;

import lombok.Data;

@Data
public class AuthzGuardResult {
    private String guardCode;
    private boolean allowed;
    private String reasonCode;
    private String reasonMessage;
}
