package com.triobase.service.auth.service;

import com.triobase.service.auth.entity.SysAuthAction;
import com.triobase.service.auth.entity.SysAuthField;
import com.triobase.service.auth.entity.SysAuthGuardTemplate;
import com.triobase.service.auth.entity.SysAuthResource;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthDecisionLogMapper;
import com.triobase.service.auth.mapper.AuthFieldMapper;
import com.triobase.service.auth.mapper.AuthFieldPolicyMapper;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.AuthGuardTemplateMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationRegistryServiceTest {

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
