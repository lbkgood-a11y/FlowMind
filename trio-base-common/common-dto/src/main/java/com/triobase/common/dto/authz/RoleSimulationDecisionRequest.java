package com.triobase.common.dto.authz;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoleSimulationDecisionRequest extends AuthorizationDecisionRequest {
    private String roleId;
    private List<String> organizationIds = new ArrayList<>();
}
