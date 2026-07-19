package com.triobase.service.openapi.service.authorization;

import com.triobase.common.dto.authz.AuthzGuardResult;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReferenceContractAuthorizationResult {
    private boolean allowed;
    private AuthorizationDecisionResponse centralDecision;
    private List<AuthzGuardResult> guardResults = new ArrayList<>();
}
