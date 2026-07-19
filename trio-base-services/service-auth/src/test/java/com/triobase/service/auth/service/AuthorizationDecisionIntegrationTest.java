package com.triobase.service.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.common.dto.authz.AuthorizationResourceSyncRequest;
import com.triobase.common.dto.authz.AuthzGuardResult;
import com.triobase.service.auth.dto.AuthorizationSyncResponse;
import com.triobase.service.auth.dto.SaveAuthorizationGrantRequest;
import com.triobase.service.auth.entity.SysAuthAction;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end composite test verifying the full sync → grant → decide pipeline
 * through real {@link AuthorizationRegistryService} and
 * {@link AuthorizationDecisionService} beans with mocked persistence.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationDecisionIntegrationTest {

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
    @Mock private AuthorizationVersionService versionService;

    private AuthorizationRegistryService registryService;
    private AuthorizationDecisionService decisionService;

    private static final String TENANT = "integration-tenant";
    private static final String USER_ID = "U_INTEGRATION";
    private static final String RESOURCE_CODE = "LOWCODE_FORM:INTEGRATION_TEST";

    @BeforeEach
    void setUp() {
        registryService = new AuthorizationRegistryService(
                resourceMapper, actionMapper, fieldMapper,
                fieldPolicyMapper, guardTemplateMapper, grantMapper,
                decisionLogMapper, versionService);
        decisionService = new AuthorizationDecisionService(
                resourceMapper, actionMapper, grantMapper,
                fieldMapper, fieldPolicyMapper, guardTemplateMapper,
                decisionLogMapper, userMapper, userRoleMapper,
                roleMapper, dataPolicyService, versionService, new ObjectMapper());
        when(versionService.current(anyString())).thenReturn(1L);
    }

    @Test
    void fullSyncGrantDecidePipeline() {
        // --- Sync: resourceMapper SELECT before upsert returns null (new) ---
        when(resourceMapper.selectOne(any())).thenReturn(null);
        // version bumps
        when(versionService.bump(anyString())).thenReturn(2L);

        AuthorizationResourceSyncRequest syncRequest = new AuthorizationResourceSyncRequest();
        syncRequest.setTenantId(TENANT);
        syncRequest.setOwnerService("service-lowcode");
        AuthorizationResourceSyncRequest.Resource resource = new AuthorizationResourceSyncRequest.Resource();
        resource.setResourceCode(RESOURCE_CODE);
        resource.setResourceType("LOWCODE_FORM");
        resource.setDisplayName("Integration Test Form");
        resource.setLifecycleStatus("ACTIVE");

        AuthorizationResourceSyncRequest.Action viewAction = new AuthorizationResourceSyncRequest.Action();
        viewAction.setActionCode("VIEW");
        viewAction.setActionCategory("DOCUMENT");
        resource.getActions().add(viewAction);

        AuthorizationResourceSyncRequest.Action approveAction = new AuthorizationResourceSyncRequest.Action();
        approveAction.setActionCode("APPROVE");
        approveAction.setActionCategory("DOCUMENT");
        approveAction.setGuardCodes(List.of("WORKFLOW_CANDIDATE"));
        resource.getActions().add(approveAction);

        AuthorizationResourceSyncRequest.Field amountField = new AuthorizationResourceSyncRequest.Field();
        amountField.setFieldKey("amount");
        amountField.setFieldLabel("Amount");
        amountField.setFieldType("number");
        amountField.setSensitivityClassification("FINANCIAL");
        amountField.setDefaultMaskStrategy("LAST4");
        resource.getFields().add(amountField);

        AuthorizationResourceSyncRequest.Guard guard = new AuthorizationResourceSyncRequest.Guard();
        guard.setGuardCode("WORKFLOW_CANDIDATE");
        guard.setOwnerService("service-workflow-engine");
        guard.setSupportedResourceTypes("LOWCODE_FORM");
        guard.setDescription("当前用户必须是待办任务候选人或处理人");
        resource.getGuards().add(guard);

        syncRequest.setResources(List.of(resource));
        AuthorizationSyncResponse syncResponse = registryService.synchronize(syncRequest);

        assertThat(syncResponse.getResourceCount()).isEqualTo(1);
        assertThat(syncResponse.getActionCount()).isEqualTo(2);
        assertThat(syncResponse.getFieldCount()).isEqualTo(1);
        assertThat(syncResponse.getGuardCount()).isEqualTo(1);
        verify(resourceMapper).insert(any(com.triobase.service.auth.entity.SysAuthResource.class));
        verify(actionMapper, org.mockito.Mockito.times(2)).insert(any(com.triobase.service.auth.entity.SysAuthAction.class));
        verify(fieldMapper).insert(any(com.triobase.service.auth.entity.SysAuthField.class));
        verify(guardTemplateMapper).insert(any(com.triobase.service.auth.entity.SysAuthGuardTemplate.class));

        // --- Grant: resource/action must already appear in DB for validation ---
        // saveGrant calls ensureActionRegistered which counts existing actions
        when(actionMapper.selectCount(any())).thenReturn(1L);
        when(resourceMapper.selectOne(any())).thenReturn(resource("ACTIVE"));

        SaveAuthorizationGrantRequest grantReq = new SaveAuthorizationGrantRequest();
        grantReq.setTenantId(TENANT);
        grantReq.setSubjectType("USER");
        grantReq.setSubjectId(USER_ID);
        grantReq.setResourceCode(RESOURCE_CODE);
        grantReq.setActionCode("VIEW");
        grantReq.setEffect("ALLOW");
        registryService.saveGrant(grantReq);

        // --- Decide ---
        // subject snapshot
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setTenantId(TENANT);
        user.setStatus(1);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        // registered action + resource
        SysAuthAction registeredAction = new SysAuthAction();
        registeredAction.setTenantId(TENANT);
        registeredAction.setResourceCode(RESOURCE_CODE);
        registeredAction.setActionCode("VIEW");
        registeredAction.setActionCategory("DOCUMENT");
        registeredAction.setGuardCodes("WORKFLOW_CANDIDATE");
        registeredAction.setStatus((short) 1);
        when(actionMapper.selectList(any())).thenReturn(List.of(registeredAction));
        when(resourceMapper.selectOne(any())).thenReturn(resource("ACTIVE"));

        // grant exists
        com.triobase.service.auth.entity.SysAuthGrant sysGrant = new com.triobase.service.auth.entity.SysAuthGrant();
        sysGrant.setId("G_SYNCED");
        sysGrant.setTenantId(TENANT);
        sysGrant.setSubjectType("USER");
        sysGrant.setSubjectId(USER_ID);
        sysGrant.setResourceCode(RESOURCE_CODE);
        sysGrant.setActionCode("VIEW");
        sysGrant.setEffect("ALLOW");
        sysGrant.setStatus((short) 1);
        when(grantMapper.selectList(any())).thenReturn(List.of(sysGrant));

        // data policy: all data
        var dimension = new com.triobase.service.auth.dto.DataPolicyDimensionResponse();
        dimension.setDimensionCode("DEFAULT");
        dimension.setScopeType("ALL");
        var policy = new com.triobase.service.auth.dto.DataPolicyResponse();
        policy.setId("DP_ALL");
        policy.setEffect("ALLOW");
        policy.setDimensions(List.of(dimension));
        var effective = new com.triobase.service.auth.dto.EffectiveDataPolicyResponse();
        effective.setUserId(USER_ID);
        effective.setResourceCode(RESOURCE_CODE);
        effective.setActionCode("VIEW");
        effective.setRestrictive(false);
        effective.setPolicies(List.of(policy));
        when(dataPolicyService.resolveEffective(TENANT, USER_ID, RESOURCE_CODE, "VIEW"))
                .thenReturn(effective);

        // guard template
        var guardTemplate = new com.triobase.service.auth.entity.SysAuthGuardTemplate();
        guardTemplate.setGuardCode("WORKFLOW_CANDIDATE");
        guardTemplate.setOwnerService("service-workflow-engine");
        guardTemplate.setDescription("当前用户必须是候选人");
        guardTemplate.setStatus((short) 1);
        when(guardTemplateMapper.selectList(any())).thenReturn(List.of(guardTemplate));

        AuthorizationDecisionRequest decideReq = new AuthorizationDecisionRequest();
        decideReq.setTenantId(TENANT);
        decideReq.setUserId(USER_ID);
        decideReq.setResourceCode(RESOURCE_CODE);
        decideReq.setActionCode("VIEW");

        AuthorizationDecisionResponse decideResp = decisionService.decide(decideReq);

        assertThat(decideResp.isAllowed()).isTrue();
        assertThat(decideResp.getGuardRequirements())
                .extracting("guardCode")
                .contains("WORKFLOW_CANDIDATE");
    }

    @Test
    void unregisteredResourceDeniesFailClosed() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setTenantId(TENANT);
        user.setStatus(1);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());
        when(actionMapper.selectList(any())).thenReturn(List.of());

        AuthorizationDecisionRequest request = new AuthorizationDecisionRequest();
        request.setTenantId(TENANT);
        request.setUserId(USER_ID);
        request.setResourceCode("LOWCODE_FORM:NOT_REGISTERED");
        request.setActionCode("VIEW");

        AuthorizationDecisionResponse response = decisionService.decide(request);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.isVisible()).isFalse();
        assertThat(response.getReasons())
                .extracting("code")
                .contains("AUTHZ_RESOURCE_ACTION_NOT_REGISTERED");
    }

    @Test
    void centralDecisionPlusGuardResultsCombinedCorrectly() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setTenantId(TENANT);
        user.setStatus(1);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        SysAuthAction registeredAction = new SysAuthAction();
        registeredAction.setTenantId(TENANT);
        registeredAction.setResourceCode(RESOURCE_CODE);
        registeredAction.setActionCode("APPROVE");
        registeredAction.setStatus((short) 1);
        when(actionMapper.selectList(any())).thenReturn(List.of(registeredAction));
        when(resourceMapper.selectOne(any())).thenReturn(resource("ACTIVE"));

        com.triobase.service.auth.entity.SysAuthGrant allowGrant = new com.triobase.service.auth.entity.SysAuthGrant();
        allowGrant.setId("G_APPROVE");
        allowGrant.setTenantId(TENANT);
        allowGrant.setSubjectType("USER");
        allowGrant.setSubjectId(USER_ID);
        allowGrant.setResourceCode(RESOURCE_CODE);
        allowGrant.setActionCode("*");
        allowGrant.setEffect("ALLOW");
        allowGrant.setStatus((short) 1);
        when(grantMapper.selectList(any())).thenReturn(List.of(allowGrant));

        var dimension = new com.triobase.service.auth.dto.DataPolicyDimensionResponse();
        dimension.setDimensionCode("DEFAULT");
        dimension.setScopeType("ALL");
        var policy = new com.triobase.service.auth.dto.DataPolicyResponse();
        policy.setId("DP_ALL");
        policy.setEffect("ALLOW");
        policy.setDimensions(List.of(dimension));
        var effective = new com.triobase.service.auth.dto.EffectiveDataPolicyResponse();
        effective.setUserId(USER_ID);
        effective.setResourceCode(RESOURCE_CODE);
        effective.setActionCode("APPROVE");
        effective.setRestrictive(false);
        effective.setPolicies(List.of(policy));
        when(dataPolicyService.resolveEffective(TENANT, USER_ID, RESOURCE_CODE, "APPROVE"))
                .thenReturn(effective);

        AuthorizationDecisionRequest decideReq = new AuthorizationDecisionRequest();
        decideReq.setTenantId(TENANT);
        decideReq.setUserId(USER_ID);
        decideReq.setResourceCode(RESOURCE_CODE);
        decideReq.setActionCode("APPROVE");

        AuthzGuardResult guardResult = new AuthzGuardResult();
        guardResult.setGuardCode("NO_SELF_APPROVAL");
        guardResult.setAllowed(false);
        guardResult.setReasonCode("NO_SELF_APPROVAL_FAILED");
        decideReq.setGuardResults(List.of(guardResult));

        AuthorizationDecisionResponse response = decisionService.decide(decideReq);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getReasons())
                .extracting("code")
                .contains("AUTHZ_ALLOW_GRANT_MATCHED", "NO_SELF_APPROVAL_FAILED");
    }

    private SysAuthResource resource(String lifecycleStatus) {
        SysAuthResource r = new SysAuthResource();
        r.setId("R_INTEGRATION");
        r.setTenantId(TENANT);
        r.setResourceCode(RESOURCE_CODE);
        r.setResourceType("LOWCODE_FORM");
        r.setOwnerService("service-lowcode");
        r.setLifecycleStatus(lifecycleStatus);
        return r;
    }
}
