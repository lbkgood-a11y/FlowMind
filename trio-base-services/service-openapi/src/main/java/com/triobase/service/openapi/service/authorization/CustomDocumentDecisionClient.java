package com.triobase.service.openapi.service.authorization;

import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;

public interface CustomDocumentDecisionClient {
    AuthorizationDecisionResponse decide(AuthorizationDecisionRequest request);
}
