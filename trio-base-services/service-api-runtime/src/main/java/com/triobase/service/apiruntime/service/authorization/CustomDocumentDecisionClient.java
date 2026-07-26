package com.triobase.service.apiruntime.service.authorization;

import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;

public interface CustomDocumentDecisionClient {
    AuthorizationDecisionResponse decide(AuthorizationDecisionRequest request);
}
