package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RoleAuthorizationProfileResponse {
    private String tenantId;
    private String roleId;
    private List<AuthorizationGrantResponse> functionGrants = new ArrayList<>();
    private List<DataPolicyResponse> dataPolicies = new ArrayList<>();
    private List<FieldPolicyResponse> fieldPolicies = new ArrayList<>();
}
