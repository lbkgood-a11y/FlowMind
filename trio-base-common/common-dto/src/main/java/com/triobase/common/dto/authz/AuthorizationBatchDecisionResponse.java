package com.triobase.common.dto.authz;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuthorizationBatchDecisionResponse {
    private List<AuthorizationDecisionResponse> decisions = new ArrayList<>();
}
