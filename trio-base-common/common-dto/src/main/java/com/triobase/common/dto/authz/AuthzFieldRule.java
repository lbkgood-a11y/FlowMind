package com.triobase.common.dto.authz;

import lombok.Data;

@Data
public class AuthzFieldRule {
    private String fieldKey;
    private String readMode;
    private String writeMode;
    private String maskStrategy;
    private String matchedPolicyId;
    private String reasonCode;
    private String reasonMessage;
}
