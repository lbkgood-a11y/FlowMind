package com.triobase.service.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.common.dto.authz.AuthzGuardResult;
import com.triobase.common.dto.authz.RoleSimulationDecisionRequest;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.auth.dto.DataPolicyDimensionResponse;
import com.triobase.service.auth.dto.DataPolicyResponse;
import com.triobase.service.auth.dto.EffectiveDataPolicyResponse;
import com.triobase.service.auth.entity.SysAuthAction;
import com.triobase.service.auth.entity.SysAuthDecisionLog;
import com.triobase.service.auth.entity.SysAuthFieldPolicy;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysAuthGuardTemplate;
import com.triobase.service.auth.entity.SysAuthResource;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.entity.SysUserRole;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthDecisionLogMapper;
import com.triobase.service.auth.mapper.AuthFieldMapper;
import com.triobase.service.auth.mapper.AuthFieldPolicyMapper;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.AuthGuardTemplateMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import com.triobase.service.auth.mapper.RoleMapper;
import com.triobase.service.auth.mapper.UserMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationDecisionServiceTest {

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisPlusTestMetadata.initialize();
    }

    @Mock private AuthResourceMapper resourceMapper;
    @Mock private AuthActionMapper actionMapper;
    @Mock private AuthGrantMapper grantMapper;
    @Mock private AuthFieldMapper fieldMapper;
    @Mock private AuthFieldPolicyMapper fieldPolicyMapper;
    @Mock private AuthGuardTemplateMapper guardTemplateMapper;
    @Mock private AuthDecisionLogMapper decisionLogMapper;
    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private DataPolicyService dataPolicyService;
    @Mock private RoleAuthorizationDataService roleAuthorizationDataService;
    @Mock private AuthorizationVersionService versionService;
    @Mock private ActiveReleaseEvidenceService activeReleaseEvidenceService;

    private AuthorizationDecisionService service;

    @Test
    void pageCapabilityModeRejectsRoleGrantWithoutActiveReleaseEvidence() {
        givenSubject("U001", "tenant-a", "R_MANAGER", "MANAGER");
        givenRegisteredAction("tenant-a", "LOWCODE_FORM:EXPENSE", "VIEW", null);
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("G_ROLE_ALLOW", "ROLE", "R_MANAGER", "ALLOW")));
        when(activeReleaseEvidenceService.batchSupportedGrantKeys(
                "tenant-a", List.of("R_MANAGER"))).thenReturn(java.util.Set.of());

        AuthorizationDecisionResponse response = service.decide(
                request("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "VIEW"));

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getDisabledReason()).isEqualTo("AUTHZ_GRANT_NOT_FOUND");
    }

    @BeforeEach
    void setUp() {
        service = new AuthorizationDecisionService(
                resourceMapper,
                actionMapper,
                grantMapper,
                fieldMapper,
                fieldPolicyMapper,
                guardTemplateMapper,
                decisionLogMapper,
                userMapper,
                userRoleMapper,
                roleMapper,
                dataPolicyService,
                roleAuthorizationDataService,
                versionService,
                activeReleaseEvidenceService,
                new ObjectMapper());
        lenient().when(versionService.current(anyString())).thenReturn(1L);
        lenient().when(activeReleaseEvidenceService.batchSupportedGrantKeys(anyString(), any()))
                .thenReturn(null); // null = non-PAGE_CAPABILITY, allow all
        lenient().when(dataPolicyService.resolveSubjectOrganizationIds(anyString(), anyString()))
                .thenReturn(List.of());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void simulateRoleUsesProductionPipelineWithoutUserAssignment() {
        SysRole role = new SysRole();
        role.setId("R_MANAGER");
        role.setRoleCode("MANAGER");
        role.setStatus((short) 1);
        when(roleMapper.selectOne(any())).thenReturn(role);
        givenRegisteredAction("tenant-a", "LOWCODE_FORM:EXPENSE", "VIEW", null);
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("G_ALLOW", "ROLE", "R_MANAGER", "ALLOW")));
        when(fieldPolicyMapper.selectList(any())).thenReturn(List.of());
        when(dataPolicyService.resolveEffectiveForRoles(
                "tenant-a", "SIMULATION:R_MANAGER", List.of("R_MANAGER"), List.of("ORG-1"),
                "LOWCODE_FORM:EXPENSE", "VIEW")).thenReturn(allDataScope());
        when(roleAuthorizationDataService.menuProjectionForRole("tenant-a", "R_MANAGER")).thenReturn(List.of());
        RoleSimulationDecisionRequest request = new RoleSimulationDecisionRequest();
        request.setTenantId("tenant-a");
        request.setRoleId("R_MANAGER");
        request.setOrganizationIds(List.of("ORG-1"));
        request.setResourceCode("LOWCODE_FORM:EXPENSE");
        request.setActionCode("VIEW");
        request.setFieldKeys(List.of("amount"));

        AuthorizationDecisionResponse response = service.simulateRole(request);

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getEvaluationMode()).isEqualTo("SIMULATION");
        assertThat(response.getSimulatedRoleId()).isEqualTo("R_MANAGER");
        assertThat(response.getSuppliedOrganizationIds()).containsExactly("ORG-1");
        verify(userRoleMapper, never()).selectList(any());
        verifyNoInteractions(decisionLogMapper);
    }

    @Test
    void simulateRoleDeniesCrossTenantPreview() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "ADMIN", "admin", "tenant-a", List.of("ADMIN"), List.of(),
                null, null, null, null, null, null));
        RoleSimulationDecisionRequest request = new RoleSimulationDecisionRequest();
        request.setTenantId("tenant-b");
        request.setRoleId("R_MANAGER");
        request.setResourceCode("LOWCODE_FORM:EXPENSE");
        request.setActionCode("VIEW");

        assertThatThrownBy(() -> service.simulateRole(request))
                .isInstanceOf(com.triobase.common.core.exception.BizException.class)
                .hasMessageContaining("AUTHZ_CROSS_TENANT_DENIED");
        verify(roleMapper, never()).selectOne(any());
    }

    @Test
    void decisionWithoutAuthenticatedOrExplicitTenantFailsClosed() {
        SysUser user = new SysUser();
        user.setId("U001");
        user.setTenantId("tenant-a");
        when(userMapper.selectById("U001")).thenReturn(user);
        AuthorizationDecisionRequest request = new AuthorizationDecisionRequest();
        request.setUserId("U001");
        request.setResourceCode("LOWCODE_FORM:EXPENSE");
        request.setActionCode("VIEW");

        assertThatThrownBy(() -> service.decide(request))
                .isInstanceOf(com.triobase.common.core.exception.BizException.class)
                .hasMessageContaining("AUTHZ_TENANT_REQUIRED");
        verify(grantMapper, never()).selectList(any());
    }

    @Test
    void equivalentActualUserAndRoleSimulationReturnSameCoreDecision() {
        SysRole role = new SysRole();
        role.setId("R_MANAGER");
        role.setRoleCode("MANAGER");
        role.setStatus((short) 1);
        when(roleMapper.selectOne(any())).thenReturn(role);
        givenSubject("U001", "tenant-a", "R_MANAGER", "MANAGER");
        givenRegisteredAction("tenant-a", "LOWCODE_FORM:EXPENSE", "VIEW", null);
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("G_ALLOW", "ROLE", "R_MANAGER", "ALLOW")));
        when(fieldPolicyMapper.selectList(any())).thenReturn(List.of(maskedFieldPolicy()));
        when(dataPolicyService.resolveEffective(
                "tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "VIEW")).thenReturn(allDataScope());
        when(dataPolicyService.resolveEffectiveForRoles(
                "tenant-a", "SIMULATION:R_MANAGER", List.of("R_MANAGER"), List.of(),
                "LOWCODE_FORM:EXPENSE", "VIEW")).thenReturn(allDataScope());
        when(roleAuthorizationDataService.menuProjectionForRole("tenant-a", "R_MANAGER")).thenReturn(List.of());
        AuthorizationDecisionRequest actualRequest = request(
                "tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "VIEW");
        actualRequest.setFieldKeys(List.of("amount"));
        RoleSimulationDecisionRequest simulationRequest = new RoleSimulationDecisionRequest();
        simulationRequest.setTenantId("tenant-a");
        simulationRequest.setRoleId("R_MANAGER");
        simulationRequest.setResourceCode("LOWCODE_FORM:EXPENSE");
        simulationRequest.setActionCode("VIEW");
        simulationRequest.setFieldKeys(List.of("amount"));

        AuthorizationDecisionResponse actual = service.decide(actualRequest);
        AuthorizationDecisionResponse simulated = service.simulateRole(simulationRequest);

        assertThat(simulated.isAllowed()).isEqualTo(actual.isAllowed());
        assertThat(simulated.getEffect()).isEqualTo(actual.getEffect());
        assertThat(simulated.getMatchedGrantId()).isEqualTo(actual.getMatchedGrantId());
        assertThat(simulated.getFieldRules())
                .extracting("fieldKey", "readMode", "writeMode")
                .containsExactlyElementsOf(actual.getFieldRules().stream()
                        .map(rule -> tuple(rule.getFieldKey(), rule.getReadMode(), rule.getWriteMode()))
                        .toList());
    }

    @Test
    void decideAllowsGrantedActionAndAuditsEnforcement() {
        givenSubject("U001", "tenant-a", "R_MANAGER", "MANAGER");
        givenRegisteredAction("tenant-a", "LOWCODE_FORM:EXPENSE", "APPROVE", "WORKFLOW_CANDIDATE");
        when(grantMapper.selectList(any())).thenReturn(List.of(grant("G_ALLOW", "ROLE", "R_MANAGER", "ALLOW")));
        when(dataPolicyService.resolveEffective("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "APPROVE"))
                .thenReturn(allDataScope());
        when(fieldPolicyMapper.selectList(any())).thenReturn(List.of(maskedFieldPolicy()));
        when(guardTemplateMapper.selectList(any())).thenReturn(List.of(guardTemplate()));

        AuthorizationDecisionRequest request = request("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "APPROVE");
        request.setFieldKeys(List.of("amount", "phone"));
        request.setEnforcementMode(true);
        request.setActionId("act_001");
        request.setActionType("lowcode.form.submit");
        request.setActionSource("GUI");
        request.setActionTargetType("LOWCODE_FORM");
        request.setActionTargetId("FORM001");
        request.setActionCorrelationId("corr_001");
        request.setActionPayloadMetadata(Map.of("payloadKeys", List.of("data")));

        AuthorizationDecisionResponse response = service.decide(request);

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.isVisible()).isTrue();
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getRenderingMetadata())
                .containsEntry("businessDocumentAction", true)
                .containsEntry("semanticActionCode", "APPROVE");
        assertThat(response.getMatchedGrantId()).isEqualTo("G_ALLOW");
        assertThat(response.getDataScope().isRestrictive()).isFalse();
        assertThat(response.getDataScope().getScopeTypes()).containsExactly("ALL");
        assertThat(response.getFieldRules())
                .extracting("fieldKey", "readMode", "writeMode")
                .contains(tuple("amount", "MASKED", "READONLY"),
                        tuple("phone", "VISIBLE", "EDITABLE"));
        assertThat(response.getGuardRequirements())
                .extracting("guardCode")
                .containsExactly("WORKFLOW_CANDIDATE");
        ArgumentCaptor<SysAuthDecisionLog> log = ArgumentCaptor.forClass(SysAuthDecisionLog.class);
        verify(decisionLogMapper).insert(log.capture());
        assertThat(log.getValue().getActionId()).isEqualTo("act_001");
        assertThat(log.getValue().getActionType()).isEqualTo("lowcode.form.submit");
        assertThat(log.getValue().getActionSource()).isEqualTo("GUI");
        assertThat(log.getValue().getActionTargetType()).isEqualTo("LOWCODE_FORM");
        assertThat(log.getValue().getActionTargetId()).isEqualTo("FORM001");
        assertThat(log.getValue().getActionCorrelationId()).isEqualTo("corr_001");
        assertThat(log.getValue().getActionPayloadMetadata()).contains("payloadKeys");
    }

    @Test
    void decideDeniesUnknownResourceFailClosed() {
        givenSubject("U001", "tenant-a", "R_MANAGER", "MANAGER");
        when(actionMapper.selectList(any())).thenReturn(List.of());

        AuthorizationDecisionResponse response = service.decide(
                request("tenant-a", "U001", "LOWCODE_FORM:UNKNOWN", "APPROVE"));

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.isVisible()).isFalse();
        assertThat(response.isEnabled()).isFalse();
        assertThat(response.getDisabledReason()).isEqualTo("AUTHZ_RESOURCE_ACTION_NOT_REGISTERED");
        assertThat(response.getReasons())
                .extracting("code")
                .contains("AUTHZ_RESOURCE_ACTION_NOT_REGISTERED");
        verify(decisionLogMapper, never()).insert(any(SysAuthDecisionLog.class));
    }

    @Test
    void decideAppliesDenyPrecedenceOverAllow() {
        givenSubject("U001", "tenant-a", "R_MANAGER", "MANAGER");
        givenRegisteredAction("tenant-a", "LOWCODE_FORM:EXPENSE", "EXPORT", null);
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("G_ALLOW", "ROLE", "R_MANAGER", "ALLOW"),
                grant("G_DENY", "USER", "U001", "DENY")));
        when(dataPolicyService.resolveEffective("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "EXPORT"))
                .thenReturn(allDataScope());

        AuthorizationDecisionResponse response = service.decide(
                request("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "EXPORT"));

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.isVisible()).isTrue();
        assertThat(response.isEnabled()).isFalse();
        assertThat(response.getDisabledReason()).isEqualTo("AUTHZ_DENY_GRANT_MATCHED");
        assertThat(response.getMatchedGrantId()).isEqualTo("G_DENY");
        assertThat(response.getReasons())
                .extracting("code")
                .contains("AUTHZ_DENY_GRANT_MATCHED");
    }

    @Test
    void decideCombinesFailedGuardResult() {
        givenSubject("U001", "tenant-a", "R_MANAGER", "MANAGER");
        givenRegisteredAction("tenant-a", "LOWCODE_FORM:EXPENSE", "APPROVE", "NO_SELF_APPROVAL");
        when(grantMapper.selectList(any())).thenReturn(List.of(grant("G_ALLOW", "ROLE", "R_MANAGER", "ALLOW")));
        when(dataPolicyService.resolveEffective("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "APPROVE"))
                .thenReturn(allDataScope());

        AuthzGuardResult guardResult = new AuthzGuardResult();
        guardResult.setGuardCode("NO_SELF_APPROVAL");
        guardResult.setAllowed(false);
        guardResult.setReasonCode("NO_SELF_APPROVAL_FAILED");
        guardResult.setReasonMessage("发起人不可自审");
        AuthorizationDecisionRequest request = request("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "APPROVE");
        request.setGuardResults(List.of(guardResult));

        AuthorizationDecisionResponse response = service.decide(request);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.isVisible()).isTrue();
        assertThat(response.isEnabled()).isFalse();
        assertThat(response.getDisabledReason()).isEqualTo("NO_SELF_APPROVAL_FAILED");
        assertThat(response.getReasons())
                .extracting("code")
                .contains("NO_SELF_APPROVAL_FAILED");
    }

    @Test
    void decideDeniesCrossTenantAccessFailClosed() {
        givenSubject("U001", "tenant-a", "R_MANAGER", "MANAGER");
        when(actionMapper.selectList(any())).thenReturn(List.of());
        // Registered in tenant-b, not tenant-a
        SysAuthAction action = new SysAuthAction();
        action.setTenantId("tenant-b");
        action.setResourceCode("CUSTOM_DOC:CONTRACT");
        action.setActionCode("VIEW");
        action.setStatus((short) 1);
        when(actionMapper.selectList(any())).thenReturn(List.of(action));

        AuthorizationDecisionResponse response = service.decide(
                request("tenant-a", "U001", "CUSTOM_DOC:CONTRACT", "VIEW"));

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.isVisible()).isFalse();
        assertThat(response.getReasons())
                .extracting("code")
                .contains("AUTHZ_RESOURCE_ACTION_NOT_REGISTERED");
    }

    @Test
    void decideAllowsAdminWithoutExplicitGrant() {
        givenSubject("U001", "tenant-a", "R_ADMIN", "ADMIN");
        givenRegisteredAction("tenant-a", "LOWCODE_FORM:EXPENSE", "VIEW", null);
        when(grantMapper.selectList(any())).thenReturn(List.of());
        when(dataPolicyService.resolveEffective("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "VIEW"))
                .thenReturn(allDataScope());

        AuthorizationDecisionResponse response = service.decide(
                request("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "VIEW"));

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getReasons())
                .extracting("code")
                .contains("AUTHZ_ADMIN_REGISTERED_RESOURCE");
    }

    @Test
    void decideCombinesMultipleGuardResults() {
        givenSubject("U001", "tenant-a", "R_MANAGER", "MANAGER");
        givenRegisteredAction("tenant-a", "LOWCODE_FORM:EXPENSE", "APPROVE",
                "WORKFLOW_CANDIDATE,NO_SELF_APPROVAL,DOCUMENT_STATUS");
        when(grantMapper.selectList(any())).thenReturn(List.of(grant("G_ALLOW", "ROLE", "R_MANAGER", "ALLOW")));
        when(dataPolicyService.resolveEffective("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "APPROVE"))
                .thenReturn(allDataScope());

        AuthzGuardResult guard1 = new AuthzGuardResult();
        guard1.setGuardCode("NO_SELF_APPROVAL");
        guard1.setAllowed(false);
        guard1.setReasonCode("NO_SELF_APPROVAL_FAILED");
        guard1.setReasonMessage("发起人不可自审");

        AuthzGuardResult guard2 = new AuthzGuardResult();
        guard2.setGuardCode("DOCUMENT_STATUS");
        guard2.setAllowed(false);
        guard2.setReasonCode("DOCUMENT_STATUS_DENIED");
        guard2.setReasonMessage("单据不在待审批状态");

        AuthorizationDecisionRequest request = request("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "APPROVE");
        request.setGuardResults(List.of(guard1, guard2));

        AuthorizationDecisionResponse response = service.decide(request);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getReasons())
                .extracting("code")
                .contains("NO_SELF_APPROVAL_FAILED", "DOCUMENT_STATUS_DENIED");
        assertThat(response.getReasons())
                .filteredOn("source", "GUARD")
                .extracting("evidenceId")
                .contains("NO_SELF_APPROVAL", "DOCUMENT_STATUS");
    }

    @Test
    void decideAppliesHiddenAndReadonlyFieldRules() {
        givenSubject("U001", "tenant-a", "R_MANAGER", "MANAGER");
        givenRegisteredAction("tenant-a", "LOWCODE_FORM:EXPENSE", "EDIT", null);
        when(grantMapper.selectList(any())).thenReturn(List.of(grant("G_ALLOW", "ROLE", "R_MANAGER", "ALLOW")));
        when(dataPolicyService.resolveEffective("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "EDIT"))
                .thenReturn(allDataScope());
        when(fieldPolicyMapper.selectList(any())).thenReturn(List.of(
                maskedFieldPolicy(),
                deniedFieldPolicy()));

        AuthorizationDecisionRequest request = request("tenant-a", "U001", "LOWCODE_FORM:EXPENSE", "EDIT");
        request.setFieldKeys(List.of("amount", "secretCost", "remark"));

        AuthorizationDecisionResponse response = service.decide(request);

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getFieldRules())
                .extracting("fieldKey", "readMode", "writeMode")
                .contains(tuple("amount", "MASKED", "READONLY"),
                        tuple("secretCost", "HIDDEN", "DENIED"),
                        tuple("remark", "VISIBLE", "EDITABLE"));
    }

    private void givenSubject(String userId, String tenantId, String roleId, String roleCode) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setStatus(1);
        when(userMapper.selectById(userId)).thenReturn(user);
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole));
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setRoleCode(roleCode);
        role.setStatus((short) 1);
        when(roleMapper.selectList(any())).thenReturn(List.of(role));
    }

    private void givenRegisteredAction(String tenantId, String resourceCode, String actionCode, String guardCodes) {
        SysAuthAction action = new SysAuthAction();
        action.setTenantId(tenantId);
        action.setResourceCode(resourceCode);
        action.setActionCode(actionCode);
        action.setGuardCodes(guardCodes);
        action.setStatus((short) 1);
        when(actionMapper.selectList(any())).thenReturn(List.of(action));
        SysAuthResource resource = new SysAuthResource();
        resource.setTenantId(tenantId);
        resource.setResourceCode(resourceCode);
        resource.setResourceType("LOWCODE_FORM");
        resource.setOwnerService("service-lowcode");
        resource.setLifecycleStatus("ACTIVE");
        when(resourceMapper.selectOne(any())).thenReturn(resource);
    }

    private SysAuthGrant grant(String id, String subjectType, String subjectId, String effect) {
        SysAuthGrant grant = new SysAuthGrant();
        grant.setId(id);
        grant.setTenantId("tenant-a");
        grant.setSubjectType(subjectType);
        grant.setSubjectId(subjectId);
        grant.setResourceCode("LOWCODE_FORM:EXPENSE");
        grant.setActionCode("*");
        grant.setEffect(effect);
        grant.setStatus((short) 1);
        return grant;
    }

    private SysAuthFieldPolicy maskedFieldPolicy() {
        SysAuthFieldPolicy policy = new SysAuthFieldPolicy();
        policy.setId("FP_AMOUNT");
        policy.setTenantId("tenant-a");
        policy.setSubjectType("ROLE");
        policy.setSubjectId("R_MANAGER");
        policy.setResourceCode("LOWCODE_FORM:EXPENSE");
        policy.setFieldKey("amount");
        policy.setReadMode("MASKED");
        policy.setWriteMode("READONLY");
        policy.setMaskStrategy("NUMBER_RANGE");
        policy.setEffect("ALLOW");
        policy.setStatus((short) 1);
        return policy;
    }

    private SysAuthFieldPolicy deniedFieldPolicy() {
        SysAuthFieldPolicy policy = new SysAuthFieldPolicy();
        policy.setId("FP_SECRET_COST");
        policy.setTenantId("tenant-a");
        policy.setSubjectType("ROLE");
        policy.setSubjectId("R_MANAGER");
        policy.setResourceCode("LOWCODE_FORM:EXPENSE");
        policy.setFieldKey("secretCost");
        policy.setEffect("DENY");
        policy.setStatus((short) 1);
        return policy;
    }

    private SysAuthGuardTemplate guardTemplate() {
        SysAuthGuardTemplate template = new SysAuthGuardTemplate();
        template.setGuardCode("WORKFLOW_CANDIDATE");
        template.setOwnerService("service-workflow-engine");
        template.setDescription("当前用户必须是候选人");
        template.setStatus((short) 1);
        return template;
    }

    private EffectiveDataPolicyResponse allDataScope() {
        DataPolicyDimensionResponse dimension = new DataPolicyDimensionResponse();
        dimension.setDimensionCode("DEFAULT");
        dimension.setScopeType("ALL");
        dimension.setOrgUnitIds(List.of());
        DataPolicyResponse policy = new DataPolicyResponse();
        policy.setId("DP_ALL");
        policy.setEffect("ALLOW");
        policy.setDimensions(List.of(dimension));
        EffectiveDataPolicyResponse response = new EffectiveDataPolicyResponse();
        response.setUserId("U001");
        response.setResourceCode("LOWCODE_FORM:EXPENSE");
        response.setActionCode("APPROVE");
        response.setRestrictive(false);
        response.setRoleIds(List.of("R_MANAGER"));
        response.setPolicies(List.of(policy));
        return response;
    }

    private AuthorizationDecisionRequest request(String tenantId,
                                                 String userId,
                                                 String resourceCode,
                                                 String actionCode) {
        AuthorizationDecisionRequest request = new AuthorizationDecisionRequest();
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setResourceCode(resourceCode);
        request.setActionCode(actionCode);
        return request;
    }

    private org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
