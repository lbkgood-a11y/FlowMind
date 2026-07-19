package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.authz.AuthzDataScopeResult;
import com.triobase.common.dto.authz.AuthzFieldRule;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.entity.LcFormInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LowcodeAuthorizationServiceTest {

    @Mock
    private AuthorizationDecisionClient decisionClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void requireFormDecisionBuildsCanonicalRequest() {
        setUser();
        AuthorizationDecisionResponse decision = allowDecision("VIEW", "ALL");
        when(decisionClient.decide(any(AuthorizationDecisionRequest.class))).thenReturn(decision);
        LowcodeAuthorizationService service = new LowcodeAuthorizationService(decisionClient, objectMapper);

        service.requireFormDecision("expense", "view", "INS001", List.of("amount"));

        ArgumentCaptor<AuthorizationDecisionRequest> captor =
                ArgumentCaptor.forClass(AuthorizationDecisionRequest.class);
        org.mockito.Mockito.verify(decisionClient).decide(captor.capture());
        AuthorizationDecisionRequest request = captor.getValue();
        assertEquals("tenant-a", request.getTenantId());
        assertEquals("U001", request.getUserId());
        assertEquals("LOWCODE_FORM:EXPENSE", request.getResourceCode());
        assertEquals("VIEW", request.getActionCode());
        assertEquals("service-lowcode", request.getOwnerService());
        assertEquals("INS001", request.getBusinessObjectId());
        assertThat(request.getFieldKeys()).containsExactly("amount");
        assertThat(request.enforcementMode()).isTrue();
    }

    @Test
    void orgScopeIsSupported() {
        LowcodeAuthorizationService service = new LowcodeAuthorizationService(decisionClient, objectMapper);
        AuthorizationDecisionResponse decision = allowDecision("VIEW", "OWN_ORG");

        assertEquals(LowcodeAuthorizationService.DataAccessMode.ORG, service.dataAccessMode(decision));
    }

    @Test
    void orgScopeDoesNotWidenInstanceAccessWithoutOwnershipMetadata() {
        setUser();
        LowcodeAuthorizationService service = new LowcodeAuthorizationService(decisionClient, objectMapper);
        AuthorizationDecisionResponse decision = allowDecision("VIEW", "OWN_ORG");
        LcFormInstance instance = new LcFormInstance();
        instance.setSubmittedBy("U999");

        assertThat(service.canAccessInstance(decision, instance)).isFalse();
    }

    @Test
    void unknownDataScopeStillFailsClosed() {
        LowcodeAuthorizationService service = new LowcodeAuthorizationService(decisionClient, objectMapper);
        AuthorizationDecisionResponse decision = allowDecision("VIEW", "CUSTOM_SCOPE_X");

        assertEquals(LowcodeAuthorizationService.DataAccessMode.DENIED, service.dataAccessMode(decision));
    }

    @Test
    void selfScopeAllowsOnlySubmittedInstancesFromCurrentUser() {
        setUser();
        LowcodeAuthorizationService service = new LowcodeAuthorizationService(decisionClient, objectMapper);
        AuthorizationDecisionResponse decision = allowDecision("VIEW", "SELF");
        LcFormInstance owned = new LcFormInstance();
        owned.setSubmittedBy("U001");
        LcFormInstance other = new LcFormInstance();
        other.setSubmittedBy("U999");

        assertEquals(LowcodeAuthorizationService.DataAccessMode.SELF, service.dataAccessMode(decision));
        assertThat(service.canAccessInstance(decision, owned)).isTrue();
        assertThat(service.canAccessInstance(decision, other)).isFalse();
    }

    @Test
    void applyReadRulesMasksAndHidesFields() throws Exception {
        LowcodeAuthorizationService service = new LowcodeAuthorizationService(decisionClient, objectMapper);
        AuthorizationDecisionResponse decision = allowDecision("VIEW", "ALL");
        decision.setFieldRules(List.of(
                fieldRule("amount", "MASKED", "EDITABLE", "LAST4"),
                fieldRule("ssn", "HIDDEN", "DENIED", null)));
        FormInstanceResponse response = new FormInstanceResponse();
        response.setDataJson("{\"amount\":\"12345678\",\"ssn\":\"123\",\"memo\":\"ok\"}");

        service.applyReadRules(response, decision);

        Map<String, Object> data = objectMapper.readValue(response.getDataJson(), new TypeReference<>() {
        });
        assertEquals("****5678", data.get("amount"));
        assertThat(data).doesNotContainKey("ssn");
        assertEquals("ok", data.get("memo"));
    }

    @Test
    void requireWritableFieldsRejectsReadOnlyRule() {
        LowcodeAuthorizationService service = new LowcodeAuthorizationService(decisionClient, objectMapper);
        AuthorizationDecisionResponse decision = allowDecision("CREATE", "SELF");
        decision.setFieldRules(List.of(fieldRule("amount", "VISIBLE", "READ_ONLY", null)));

        BizException exception = assertThrows(BizException.class,
                () -> service.requireWritableFields(decision, Map.of("amount", 12)));

        assertEquals("LOWCODE_FIELD_WRITE_DENIED", exception.getMessage());
    }

    @Test
    void requireWritableFieldsRejectsDeniedRule() {
        LowcodeAuthorizationService service = new LowcodeAuthorizationService(decisionClient, objectMapper);
        AuthorizationDecisionResponse decision = allowDecision("CREATE", "SELF");
        decision.setFieldRules(List.of(fieldRule("amount", "VISIBLE", "DENIED", null)));

        BizException exception = assertThrows(BizException.class,
                () -> service.requireWritableFields(decision, Map.of("amount", 12)));

        assertEquals("LOWCODE_FIELD_WRITE_DENIED", exception.getMessage());
    }

    private void setUser() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", "tenant-a", List.of(), List.of(), null, null, null));
    }

    private AuthorizationDecisionResponse allowDecision(String actionCode, String scopeType) {
        AuthorizationDecisionResponse decision = new AuthorizationDecisionResponse();
        decision.setAllowed(true);
        decision.setTenantId("tenant-a");
        decision.setUserId("U001");
        decision.setResourceCode("LOWCODE_FORM:EXPENSE");
        decision.setActionCode(actionCode);
        AuthzDataScopeResult dataScope = new AuthzDataScopeResult();
        dataScope.setRestrictive(false);
        dataScope.setOrgContextResolved(true);
        dataScope.setScopeTypes(List.of(scopeType));
        decision.setDataScope(dataScope);
        return decision;
    }

    private AuthzFieldRule fieldRule(String fieldKey,
                                     String readMode,
                                     String writeMode,
                                     String maskStrategy) {
        AuthzFieldRule rule = new AuthzFieldRule();
        rule.setFieldKey(fieldKey);
        rule.setReadMode(readMode);
        rule.setWriteMode(writeMode);
        rule.setMaskStrategy(maskStrategy);
        return rule;
    }
}
