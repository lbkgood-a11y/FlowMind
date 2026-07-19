package com.triobase.common.dto.authz;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuthorizationBatchDecisionRequest {
    private List<AuthorizationDecisionRequest> decisions = new ArrayList<>();
}
