package com.triobase.common.dto.authz;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AuthorizationDecisionResponse {
    private boolean allowed;
    private boolean visible = true;
    private boolean enabled = true;
    private String disabledReason;
    private String displayReason;
    private String decisionId;
    private String tenantId;
    private String userId;
    private String resourceCode;
    private String actionCode;
    private String ownerService;
    private String businessObjectId;
    private String matchedGrantId;
    private String effect;
    private AuthzDataScopeResult dataScope;
    private List<AuthzFieldRule> fieldRules = new ArrayList<>();
    private List<AuthzGuardRequirement> guardRequirements = new ArrayList<>();
    private List<AuthzGuardResult> guardResults = new ArrayList<>();
    private List<AuthzDecisionReason> reasons = new ArrayList<>();
    private Map<String, Object> renderingMetadata = new LinkedHashMap<>();
    private Long authorizationVersion;
    private Long roleVersion;
    private Long dataPolicyVersion;
    private Long fieldPolicyVersion;
    private Long guardTemplateVersion;
}
