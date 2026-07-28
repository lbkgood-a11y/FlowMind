package com.triobase.common.dto.authz;

import com.triobase.common.core.auth.FieldRule;
import lombok.Data;

@Data
public class AuthzFieldRule implements FieldRule {
    private String fieldKey;
    private String readMode;
    private String writeMode;
    private String maskStrategy;
    private String matchedPolicyId;
    private String reasonCode;
    private String reasonMessage;
}
