package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.authz.AuthzDataScopeResult;
import com.triobase.common.dto.authz.AuthzFieldRule;
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

    private static final String DEFAULT_TENANT_ID = "default";
    private static final String OWNER_SERVICE = "service-lowcode";
    private static final String MASK_PLACEHOLDER = "******";
    private static final Set<String> READ_DENIED_MODES = Set.of("HIDDEN", "DENIED");
    private static final Set<String> WRITE_DENIED_MODES = Set.of("READ_ONLY", "DENIED");

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
        request.setBusinessObjectId(normalizeBlank(businessObjectId));
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
        return mode == DataAccessMode.ALL || mode == DataAccessMode.SELF;
    }

    public boolean canAccessInstance(AuthorizationDecisionResponse decision, LcFormInstance instance) {
        DataAccessMode mode = dataAccessMode(decision);
        if (mode == DataAccessMode.ALL) {
            return true;
        }
        if (mode == DataAccessMode.ORG) {
            return false;
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
        return ids != null ? ids : List.of();
    }

    private boolean hasOrgScope(Set<String> normalized) {
        for (String type : normalized) {
            if (type.contains("ORG") || type.contains("DEPT") || type.contains("OWN")) {
                return true;
            }
        }
        return false;
    }

    public void requireWritableFields(AuthorizationDecisionResponse decision, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Map<String, AuthzFieldRule> rules = fieldRulesByKey(decision);
        for (String fieldKey : data.keySet()) {
            AuthzFieldRule rule = rules.get(fieldKey);
            if (rule == null) {
                continue;
            }
            String writeMode = normalizeAction(rule.getWriteMode());
            if (WRITE_DENIED_MODES.contains(writeMode)) {
                throw new BizException(40392, "LOWCODE_FIELD_WRITE_DENIED");
            }
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
        for (AuthzFieldRule rule : decision.getFieldRules()) {
            if (rule == null || !StringUtils.hasText(rule.getFieldKey())) {
                continue;
            }
            String fieldKey = rule.getFieldKey();
            String readMode = normalizeAction(rule.getReadMode());
            if (READ_DENIED_MODES.contains(readMode)) {
                data.remove(fieldKey);
            } else if ("MASKED".equals(readMode) && data.containsKey(fieldKey)) {
                data.put(fieldKey, mask(data.get(fieldKey), rule.getMaskStrategy()));
            }
        }
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

    private Map<String, AuthzFieldRule> fieldRulesByKey(AuthorizationDecisionResponse decision) {
        if (decision == null || decision.getFieldRules() == null || decision.getFieldRules().isEmpty()) {
            return Map.of();
        }
        Map<String, AuthzFieldRule> rules = new LinkedHashMap<>();
        for (AuthzFieldRule rule : decision.getFieldRules()) {
            if (rule != null && StringUtils.hasText(rule.getFieldKey())) {
                rules.put(rule.getFieldKey(), rule);
            }
        }
        return rules;
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

    private Object mask(Object value, String strategy) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        String normalized = normalizeAction(strategy);
        if ("LAST4".equals(normalized)) {
            return last4(text);
        }
        if ("PHONE".equals(normalized)) {
            return phone(text);
        }
        if ("EMAIL".equals(normalized)) {
            return email(text);
        }
        return MASK_PLACEHOLDER;
    }

    private String last4(String text) {
        if (text.length() <= 4) {
            return MASK_PLACEHOLDER;
        }
        return "*".repeat(text.length() - 4) + text.substring(text.length() - 4);
    }

    private String phone(String text) {
        if (text.length() < 7) {
            return MASK_PLACEHOLDER;
        }
        return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
    }

    private String email(String text) {
        int at = text.indexOf('@');
        if (at <= 0) {
            return MASK_PLACEHOLDER;
        }
        String first = text.substring(0, 1);
        return first + "***" + text.substring(at);
    }

    private String currentTenantId() {
        String tenantId = SecurityContextHolder.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
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
                .map(this::normalizeBlank)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String normalizeAction(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public enum DataAccessMode {
        ALL,
        SELF,
        ORG,
        DENIED
    }
}
