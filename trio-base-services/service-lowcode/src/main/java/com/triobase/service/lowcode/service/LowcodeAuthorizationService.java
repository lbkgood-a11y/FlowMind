package com.triobase.service.lowcode.service;

import com.triobase.common.core.util.StringHelpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.auth.FieldMaskHelper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.authz.AuthzDataScopeResult;

import com.triobase.common.dto.authz.AuthorizationBatchDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionResponse;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.entity.LcFormInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LowcodeAuthorizationService {

    private static final String OWNER_SERVICE = "service-lowcode";

    private final AuthorizationDecisionClient decisionClient;
    private final ObjectMapper objectMapper;

    public AuthorizationDecisionResponse requireFormDecision(String formKey,
                                                             String actionCode,
                                                             String businessObjectId,
                                                             Collection<String> fieldKeys) {
        AuthorizationDecisionResponse decision = decideForm(formKey, actionCode, businessObjectId, fieldKeys);
        if (!decision.isAllowed()) {
            throw new BizException(40390, "LOWCODE_AUTHZ_DECISION_DENIED");
        }
        return decision;
    }

    public AuthorizationDecisionResponse decideForm(String formKey,
                                                    String actionCode,
                                                    String businessObjectId,
                                                    Collection<String> fieldKeys) {
        return decisionClient.decide(decisionRequest(formResourceCode(formKey), actionCode, businessObjectId, fieldKeys));
    }

    public AuthorizationDecisionResponse decideResource(String resourceCode,
                                                        String actionCode,
                                                        String businessObjectId,
                                                        Collection<String> fieldKeys) {
        return decisionClient.decide(decisionRequest(resourceCode, actionCode, businessObjectId, fieldKeys));
    }

    public AuthorizationBatchDecisionResponse batchDecide(List<AuthorizationDecisionRequest> decisions) {
        AuthorizationBatchDecisionRequest request = new AuthorizationBatchDecisionRequest();
        request.setDecisions(decisions != null ? decisions : List.of());
        return decisionClient.batchDecide(request);
    }

    public AuthorizationDecisionRequest decisionRequest(String resourceCode,
                                                        String actionCode,
                                                        String businessObjectId,
                                                        Collection<String> fieldKeys) {
        AuthorizationDecisionRequest request = new AuthorizationDecisionRequest();
        request.setTenantId(currentTenantId());
        request.setUserId(requireCurrentUser());
        request.setResourceCode(resourceCode);
        request.setActionCode(normalizeAction(actionCode));
        request.setOwnerService(OWNER_SERVICE);
        request.setBusinessObjectId(StringHelpers.normalizeBlank(businessObjectId));
        request.setFieldKeys(normalizeFieldKeys(fieldKeys));
        request.setEnforcementMode(true);
        return request;
    }

    public DataAccessMode dataAccessMode(AuthorizationDecisionResponse decision) {
        if (decision == null || !decision.isAllowed()) {
            return DataAccessMode.DENIED;
        }
        AuthzDataScopeResult dataScope = decision.getDataScope();
        if (dataScope == null || dataScope.isRestrictive()) {
            return DataAccessMode.DENIED;
        }
        List<String> scopeTypes = dataScope.getScopeTypes() == null ? List.of() : dataScope.getScopeTypes();
        Set<String> normalized = scopeTypes.stream()
                .filter(StringUtils::hasText)
                .map(scopeType -> scopeType.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (normalized.contains("ALL")) {
            return DataAccessMode.ALL;
        }
        if (normalized.contains("SELF")) {
            return DataAccessMode.SELF;
        }
        if (hasOrgScope(normalized)) {
            return DataAccessMode.ORG;
        }
        return DataAccessMode.DENIED;
    }

    public boolean allowsCreate(AuthorizationDecisionResponse decision) {
        DataAccessMode mode = dataAccessMode(decision);
        return mode == DataAccessMode.ALL || mode == DataAccessMode.SELF
                || (mode == DataAccessMode.ORG && primarySubjectOrganizationId(decision) != null);
    }

    public boolean canAccessInstance(AuthorizationDecisionResponse decision, LcFormInstance instance) {
        DataAccessMode mode = dataAccessMode(decision);
        if (mode == DataAccessMode.ALL) {
            return true;
        }
        if (mode == DataAccessMode.ORG) {
            // ORG-level row filtering is done at SQL level by DataScopeInnerInterceptor.
            // Instance-level ORG check requires an org column (e.g. org_unit_id) on lc_form_instance — not yet present.
            // A single-record lookup cannot inherit list-query SQL filtering.
            // Without verified instance ownership metadata, fail closed.
            return instance != null
                    && StringUtils.hasText(instance.getOwnerOrgId())
                    && orgUnitIds(decision).contains(instance.getOwnerOrgId());
        }
        return mode == DataAccessMode.SELF
                && instance != null
                && requireCurrentUser().equals(instance.getSubmittedBy());
    }

    public List<String> orgUnitIds(AuthorizationDecisionResponse decision) {
        if (decision == null || decision.getDataScope() == null) {
            return List.of();
        }
        List<String> ids = decision.getDataScope().getOrgUnitIds();
        return ids != null
                ? ids.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList()
                : List.of();
    }

    public String primarySubjectOrganizationId(AuthorizationDecisionResponse decision) {
        if (decision == null || decision.getSuppliedOrganizationIds() == null) {
            return null;
        }
        return decision.getSuppliedOrganizationIds().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private static final Set<String> ORG_SCOPE_TYPES = Set.of(
            "OWN_ORG", "OWN_ORG_AND_CHILDREN", "ASSIGNED_ORGS", "PARTICIPATED", "CANDIDATE_TASKS");

    private boolean hasOrgScope(Set<String> normalized) {
        for (String type : normalized) {
            if (ORG_SCOPE_TYPES.contains(type)) {
                return true;
            }
        }
        return false;
    }

    public void requireWritableFields(AuthorizationDecisionResponse decision, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        try {
            FieldMaskHelper.assertWritableFields(data,
                    decision != null ? decision.getFieldRules() : null);
        } catch (IllegalArgumentException e) {
            throw new BizException(40392, "LOWCODE_FIELD_WRITE_DENIED");
        }
    }

    public FormInstanceResponse applyReadRules(FormInstanceResponse response, AuthorizationDecisionResponse decision) {
        if (response == null || decision == null || decision.getFieldRules() == null || decision.getFieldRules().isEmpty()
                || !StringUtils.hasText(response.getDataJson())) {
            return response;
        }
        Map<String, Object> data = readData(response.getDataJson());
        if (data.isEmpty()) {
            return response;
        }
        data = FieldMaskHelper.applyReadRules(data, decision.getFieldRules());
        response.setDataJson(writeData(data));
        return response;
    }

    public String resourceCode(String formKey) {
        return formResourceCode(formKey);
    }

    public String formResourceCode(String formKey) {
        if (!StringUtils.hasText(formKey)) {
            throw new BizException(40090, "LOWCODE_AUTHZ_FORM_KEY_REQUIRED");
        }
        return "LOWCODE_FORM:" + formKey.trim().toUpperCase(Locale.ROOT);
    }

    public String appResourceCode(String appKey) {
        if (!StringUtils.hasText(appKey)) {
            throw new BizException(40090, "LOWCODE_AUTHZ_APP_KEY_REQUIRED");
        }
        return "LOWCODE_APP:" + appKey.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> readData(String dataJson) {
        try {
            return objectMapper.readValue(dataJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception exception) {
            throw new BizException(50091, "LOWCODE_AUTHZ_FIELD_READ_FAILED");
        }
    }

    private String writeData(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new BizException(50091, "LOWCODE_AUTHZ_FIELD_READ_FAILED");
        }
    }

    private String currentTenantId() {
        String tenantId = SecurityContextHolder.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(40310, "FORM_DATA_TENANT_REQUIRED");
        }
        return tenantId.trim();
    }

    private String requireCurrentUser() {
        String userId = SecurityContextHolder.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BizException(40310, "FORM_DATA_LOGIN_REQUIRED");
        }
        return userId;
    }

    private List<String> normalizeFieldKeys(Collection<String> fieldKeys) {
        if (fieldKeys == null || fieldKeys.isEmpty()) {
            return List.of();
        }
        return fieldKeys.stream()
                .map(StringHelpers::normalizeBlank)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String normalizeAction(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }


    public enum DataAccessMode {
        ALL,
        SELF,
        ORG,
        DENIED
    }
}
