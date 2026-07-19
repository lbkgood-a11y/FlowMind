package com.triobase.common.dto.authz;

import lombok.Data;

@Data
public class AuthzDecisionReason {
    private String code;
    private String message;
    private String source;
    private String evidenceId;

    public static AuthzDecisionReason of(String code, String message, String source, String evidenceId) {
        AuthzDecisionReason reason = new AuthzDecisionReason();
        reason.setCode(code);
        reason.setMessage(message);
        reason.setSource(source);
        reason.setEvidenceId(evidenceId);
        return reason;
    }
}
