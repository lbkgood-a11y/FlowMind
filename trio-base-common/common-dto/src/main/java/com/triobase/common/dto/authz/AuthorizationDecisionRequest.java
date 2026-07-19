package com.triobase.common.dto.authz;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AuthorizationDecisionRequest {
    private String tenantId;
    private String userId;
    private String resourceCode;
    private String actionCode;
    private String ownerService;
    private String businessObjectId;
    private String actionId;
    private String actionType;
    private String actionSource;
    private String actionTargetType;
    private String actionTargetId;
    private String actionCorrelationId;
    private Map<String, Object> actionPayloadMetadata = new LinkedHashMap<>();
    private List<String> fieldKeys = new ArrayList<>();
    private List<AuthzGuardResult> guardResults = new ArrayList<>();
    private Map<String, Object> attributes;
    private Boolean enforcementMode;
    private Boolean previewMode;

    public boolean enforcementMode() {
        return Boolean.TRUE.equals(enforcementMode);
    }

    public boolean previewMode() {
        return Boolean.TRUE.equals(previewMode);
    }
}
