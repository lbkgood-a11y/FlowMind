package com.triobase.service.action.service;

import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;

public interface ActionAuthorizationClient {

    AuthorizationDecisionResponse decide(AuthorizationDecisionRequest request);
}
