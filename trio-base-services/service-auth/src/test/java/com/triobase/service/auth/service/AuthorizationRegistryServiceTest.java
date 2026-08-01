package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.auth.dto.ReplaceRoleFunctionGrantsRequest;
import com.triobase.service.auth.dto.SaveFieldPolicyRequest;
import com.triobase.common.dto.authz.AuthorizationResourceSyncRequest;
import com.triobase.service.auth.entity.SysAuthAction;
import com.triobase.service.auth.entity.SysAuthField;
import com.triobase.service.auth.entity.SysAuthFieldPolicy;
import com.triobase.service.auth.entity.SysAuthGuardTemplate;
import com.triobase.service.auth.entity.SysAuthResource;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthDecisionLogMapper;
import com.triobase.service.auth.mapper.AuthFieldMapper;
import com.triobase.service.auth.mapper.AuthFieldPolicyMapper;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.AuthGuardTemplateMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationRegistryServiceTest {

    @BeforeAll
    static void initMybatisPlusMetadata() {
        initTableInfo(AuthResourceMapper.class, SysAuthResource.class);
        initTableInfo(AuthActionMapper.class, SysAuthAction.class);
        initTableInfo(AuthFieldMapper.class, SysAuthField.class);
        initTableInfo(AuthGuardTemplateMapper.class, SysAuthGuardTemplate.class);
        initTableInfo(AuthGrantMapper.class, SysAuthGrant.class);
    }

    @Mock private AuthResourceMapper resourceMapper;
    @Mock private AuthActionMapper actionMapper;
    @Mock private AuthFieldMapper fieldMapper;
    @Mock private AuthFieldPolicyMapper fieldPolicyMapper;
    @Mock private AuthGuardTemplateMapper guardTemplateMapper;
    @Mock private AuthGrantMapper grantMapper;
    @Mock private AuthDecisionLogMapper decisionLogMapper;
    @Mock private AuthorizationVersionService versionService;

    private AuthorizationRegistryService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationRegistryService(
                resourceMapper,
                actionMapper,
                fieldMapper,
                fieldPolicyMapper,
                guardTemplateMapper,
                grantMapper,
                decisionLogMapper,
                versionService);
    }

    @Test
    void resourceTreeGroupsResourcesWithActionsFieldsAndGuards() {
        when(resourceMapper.selectList(any())).thenReturn(List.of(
                resource("R_FORM", "LOWCODE_FORM:EXPENSE", "LOWCODE_FORM", "service-lowcode"),
                resource("R_CONTRACT", "CUSTOM_DOC:CONTRACT", "CUSTOM_DOC", "service-openapi")));
        when(actionMapper.selectList(any())).thenReturn(List.of(
                action("LOWCODE_FORM:EXPENSE", "VIEW", "DOCUMENT", "DOCUMENT_STATUS"),
                action("CUSTOM_DOC:CONTRACT", "APPROVE", "DOCUMENT",
                        "WORKFLOW_CANDIDATE,NO_SELF_APPROVAL,DOCUMENT_STATUS")));
        when(fieldMapper.selectList(any())).thenReturn(List.of(
                field("LOWCODE_FORM:EXPENSE", "amount"),
                field("CUSTOM_DOC:CONTRACT", "paymentTerms")));
        when(guardTemplateMapper.selectList(any())).thenReturn(List.of(
                guard("DOCUMENT_STATUS", "LOWCODE_FORM,CUSTOM_DOC"),
                guard("WORKFLOW_CANDIDATE", "CUSTOM_DOC,WORKFLOW_TASK")));

        var tree = service.resourceTree("tenant-a", null);

        assertThat(tree.getTenantId()).isEqualTo("tenant-a");
        assertThat(tree.getGroups()).extracting("resourceType")
                .containsExactly("LOWCODE_FORM", "CUSTOM_DOC");
        var customDoc = tree.getGroups().get(1).getResources().getFirst();
        assertThat(customDoc.getResourceCode()).isEqualTo("CUSTOM_DOC:CONTRACT");
        assertThat(customDoc.getReadHideEnforced()).isFalse();
        assertThat(customDoc.getReadMaskEnforced()).isFalse();
        assertThat(customDoc.getWriteDenyEnforced()).isFalse();
        assertThat(customDoc.getActions().getFirst().getGuardCodes())
                .containsExactly("WORKFLOW_CANDIDATE", "NO_SELF_APPROVAL", "DOCUMENT_STATUS");
        assertThat(customDoc.getFields()).extracting("fieldKey").containsExactly("paymentTerms");
        assertThat(customDoc.getGuards()).extracting("guardCode")
                .containsExactly("DOCUMENT_STATUS", "WORKFLOW_CANDIDATE");
    }

    @Test
    void adminOptionsExposeBusinessLabelsForFourConfigurationTabs() {
        when(guardTemplateMapper.selectList(any())).thenReturn(List.of(guard("NO_SELF_APPROVAL", "CUSTOM_DOC")));

        var options = service.adminOptions("tenant-a", null);

        assertThat(options.getFunctionActions()).extracting("code").contains("VIEW", "APPROVE", "EXPORT");
        assertThat(options.getDataScopes()).extracting("code")
                .contains("SELF", "OWN_ORG", "ASSIGNED_ORGS", "CANDIDATE_TASKS", "ALL");
        assertThat(options.getFieldReadModes()).extracting("code").containsExactly("VISIBLE", "MASKED", "HIDDEN");
        assertThat(options.getFieldWriteModes()).extracting("code").contains("EDITABLE", "READ_ONLY", "DENIED");
        assertThat(options.getGuardTemplates()).extracting("guardCode").containsExactly("NO_SELF_APPROVAL");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void missingTenantNeverFallsBackToDefault() {
        BizException exception = assertThrows(BizException.class, () -> service.effectiveTenant(null));
        assertThat(exception.getMessage()).isEqualTo("AUTHZ_TENANT_REQUIRED");
    }

    @Test
    void resourceTreeExposesVerifiedFieldEnforcementCapabilities() {
        SysAuthResource capable = resource(
                "R_FORM", "LOWCODE_FORM:EXPENSE", "LOWCODE_FORM", "service-lowcode");
        capable.setReadHideEnforced((short) 1);
        capable.setReadMaskEnforced((short) 1);
        capable.setWriteDenyEnforced((short) 1);
        when(resourceMapper.selectList(any())).thenReturn(List.of(capable));
        when(actionMapper.selectList(any())).thenReturn(List.of());
        when(fieldMapper.selectList(any())).thenReturn(List.of());
        when(guardTemplateMapper.selectList(any())).thenReturn(List.of());

        var node = service.resourceTree("tenant-a", null)
                .getGroups().getFirst().getResources().getFirst();

        assertThat(node.getReadHideEnforced()).isTrue();
        assertThat(node.getReadMaskEnforced()).isTrue();
        assertThat(node.getWriteDenyEnforced()).isTrue();
    }

    @Test
    void synchronizeRejectsUnverifiedFieldCapabilityAdvertisement() {
        AuthorizationResourceSyncRequest.Resource resource = new AuthorizationResourceSyncRequest.Resource();
        resource.setResourceCode("CUSTOM_DOC:UNVERIFIED");
        resource.setResourceType("CUSTOM_DOC");
        resource.setReadHideEnforced(true);
        AuthorizationResourceSyncRequest request = new AuthorizationResourceSyncRequest();
        request.setTenantId("tenant-a");
        request.setOwnerService("service-unverified");
        request.setResources(List.of(resource));

        assertThrows(BizException.class, () -> service.synchronize(request));
        verify(resourceMapper, never()).insert(any(SysAuthResource.class));
    }

    @Test
    void saveFieldPolicyRejectsMaskingWhenOwnerIsNotReady() {
        SysAuthResource resource = resource("R_USER", "USER", "BUSINESS_OBJECT", "service-auth");
        resource.setReadHideEnforced((short) 1);
        resource.setReadMaskEnforced((short) 0);
        resource.setWriteDenyEnforced((short) 1);
        when(resourceMapper.selectOne(any())).thenReturn(resource);
        when(fieldMapper.selectCount(any())).thenReturn(1L);

        SaveFieldPolicyRequest request = new SaveFieldPolicyRequest();
        request.setTenantId("tenant-a");
        request.setSubjectType("ROLE");
        request.setSubjectId("ADMIN");
        request.setResourceCode("USER");
        request.setFieldKey("phone");
        request.setReadMode("MASKED");
        request.setWriteMode("EDITABLE");
        request.setMaskStrategy("LAST4");
        request.setEffect("ALLOW");

        BizException exception = assertThrows(BizException.class, () -> service.saveFieldPolicy(request));

        assertThat(exception.getMessage()).isEqualTo("AUTHZ_FIELD_ENFORCEMENT_NOT_READY");
        verify(fieldPolicyMapper, never()).insert(any(SysAuthFieldPolicy.class));
    }

    @Test
    void replaceRoleFunctionGrantsRejectsStaleVersionWithoutMutation() {
        when(resourceMapper.selectCount(any())).thenReturn(1L);
        when(actionMapper.selectCount(any())).thenReturn(1L);
        when(versionService.bumpIfExpected(AuthorizationVersionService.GRANT, 7L)).thenReturn(-1L);
        ReplaceRoleFunctionGrantsRequest request = replacementRequest(7L, "LOWCODE_FORM:EXPENSE", "VIEW");

        assertThrows(BizException.class, () -> service.replaceRoleFunctionGrants("R001", request));

        verify(grantMapper, never()).delete(any());
        verify(grantMapper, never()).insert(any(SysAuthGrant.class));
    }

    @Test
    void replaceRoleFunctionGrantsClaimsVersionBeforeMutation() {
        when(resourceMapper.selectCount(any())).thenReturn(1L);
        when(actionMapper.selectCount(any())).thenReturn(1L);
        when(versionService.bumpIfExpected(AuthorizationVersionService.GRANT, 8L)).thenReturn(9L);
        when(versionService.bump(AuthorizationVersionService.AUTHORIZATION)).thenReturn(12L);

        var response = service.replaceRoleFunctionGrants(
                "R001", replacementRequest(8L, "LOWCODE_FORM:EXPENSE", "VIEW"));

        assertThat(response.getGrantVersion()).isEqualTo(9L);
        assertThat(response.getAuthorizationVersion()).isEqualTo(12L);
        verify(versionService, never()).bump(AuthorizationVersionService.GRANT);
        verify(grantMapper).delete(any());
        verify(grantMapper).insert(any(SysAuthGrant.class));
    }

    @Test
    void replaceRoleFunctionGrantsValidatesAllActionsBeforeMutation() {
        when(resourceMapper.selectCount(any())).thenReturn(0L);
        ReplaceRoleFunctionGrantsRequest request = replacementRequest(8L, "UNKNOWN", "VIEW");

        assertThrows(BizException.class, () -> service.replaceRoleFunctionGrants("R001", request));

        verify(grantMapper, never()).delete(any());
        verify(grantMapper, never()).insert(any(SysAuthGrant.class));
        verify(versionService, never()).bumpIfExpected(any(), anyLong());
    }

    private ReplaceRoleFunctionGrantsRequest replacementRequest(
            Long version, String resourceCode, String actionCode) {
        ReplaceRoleFunctionGrantsRequest.GrantItem item = new ReplaceRoleFunctionGrantsRequest.GrantItem();
        item.setResourceCode(resourceCode);
        item.setActionCode(actionCode);
        ReplaceRoleFunctionGrantsRequest request = new ReplaceRoleFunctionGrantsRequest();
        request.setTenantId("tenant-a");
        request.setExpectedGrantVersion(version);
        request.setGrants(List.of(item));
        return request;
    }

    private static void initTableInfo(Class<?> mapperType, Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        assistant.setCurrentNamespace(mapperType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    private SysAuthResource resource(String id, String code, String type, String ownerService) {
        SysAuthResource resource = new SysAuthResource();
        resource.setId(id);
        resource.setTenantId("tenant-a");
        resource.setResourceCode(code);
        resource.setResourceType(type);
        resource.setOwnerService(ownerService);
        resource.setDisplayName(code);
        resource.setLifecycleStatus("ACTIVE");
        return resource;
    }

    private SysAuthAction action(String resourceCode, String actionCode, String category, String guardCodes) {
        SysAuthAction action = new SysAuthAction();
        action.setTenantId("tenant-a");
        action.setResourceCode(resourceCode);
        action.setActionCode(actionCode);
        action.setActionCategory(category);
        action.setGuardCodes(guardCodes);
        action.setStatus((short) 1);
        return action;
    }

    private SysAuthField field(String resourceCode, String fieldKey) {
        SysAuthField field = new SysAuthField();
        field.setTenantId("tenant-a");
        field.setResourceCode(resourceCode);
        field.setFieldKey(fieldKey);
        field.setFieldLabel(fieldKey);
        field.setStatus((short) 1);
        return field;
    }

    private SysAuthGuardTemplate guard(String guardCode, String supportedResourceTypes) {
        SysAuthGuardTemplate guard = new SysAuthGuardTemplate();
        guard.setTenantId("tenant-a");
        guard.setGuardCode(guardCode);
        guard.setOwnerService("service-auth");
        guard.setSupportedResourceTypes(supportedResourceTypes);
        guard.setStatus((short) 1);
        return guard;
    }
}
