package com.triobase.common.dto.authz;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuthzDataScopeResult {
    private boolean restrictive = true;
    private boolean orgContextResolved;
    private List<String> scopeTypes = new ArrayList<>();
    private List<String> orgUnitIds = new ArrayList<>();
    private List<String> roleIds = new ArrayList<>();
    private List<String> policyIds = new ArrayList<>();
}
