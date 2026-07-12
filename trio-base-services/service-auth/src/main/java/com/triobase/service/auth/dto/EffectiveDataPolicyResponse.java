package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class EffectiveDataPolicyResponse {
    private String userId;
    private String resourceCode;
    private String actionCode;
    private boolean restrictive;
    private boolean orgContextResolved;
    private List<String> roleIds;
    private List<DataPolicyResponse> policies;
}
