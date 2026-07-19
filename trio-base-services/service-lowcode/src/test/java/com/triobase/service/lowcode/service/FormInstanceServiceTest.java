package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.service.lowcode.action.LowcodeActionExecutionContext;
import com.triobase.service.lowcode.dto.BindFormProcessRequest;
import com.triobase.service.lowcode.dto.FormFieldValidationError;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.dto.SubmitFormInstanceRequest;
import com.triobase.service.lowcode.dto.UpdateFormInstanceRequest;
import com.triobase.service.lowcode.dto.UpdateWorkflowStatusRequest;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.entity.LcFormInstance;
import com.triobase.service.lowcode.entity.LcFormInstanceWorkflowAudit;
import com.triobase.service.lowcode.exception.FormDataValidationException;
import com.triobase.service.lowcode.mapper.FormInstanceMapper;
import com.triobase.service.lowcode.mapper.FormInstanceWorkflowAuditMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormInstanceServiceTest {

    @Mock
    private FormDefinitionService formDefinitionService;
    @Mock
    private FormInstanceMapper formInstanceMapper;
    @Mock
    private FormInstanceWorkflowAuditMapper workflowAuditMapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private LowcodeFormDataValidator formDataValidator;
    @Mock
    private LowcodeAuthorizationService authorizationService;

    @InjectMocks
    private FormInstanceService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
        LowcodeActionExecutionContext.clear();
    }

    @Test
    void listUsesDatabasePagination() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", "tenant-a", List.of(), List.of(), null, null, null));
        AuthorizationDecisionResponse decision = allowDecision("VIEW");
        when(authorizationService.requireFormDecision(eq("expense"), eq("VIEW"), isNull(), any()))
                .thenReturn(decision);
        when(authorizationService.dataAccessMode(decision))
                .thenReturn(LowcodeAuthorizationService.DataAccessMode.ALL);
        when(authorizationService.applyReadRules(any(FormInstanceResponse.class), eq(decision)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LcFormInstance instance = new LcFormInstance();
        instance.setId("INS001");
        instance.setTenantId("tenant-a");
        instance.setFormKey("expense");
        instance.setSubmittedAt(LocalDateTime.now());
        Page<LcFormInstance> page = new Page<>(2, 5, 11);
        page.setRecords(List.of(instance));
        when(formInstanceMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        var result = service.list("expense", 2, 5);

        assertThat(result.getTotal()).isEqualTo(11);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().getFirst().getId()).isEqualTo("INS001");
        verify(formInstanceMapper).selectPage(any(Page.class), any(Wrapper.class));
        verify(formInstanceMapper, never()).selectList(any());
    }

    @Test
    void listFailsClosedForOrganizationScopeUntilOwnershipPredicateExists() {
        setTenantUser();
        AuthorizationDecisionResponse decision = allowDecision("VIEW");
        when(authorizationService.requireFormDecision(eq("expense"), eq("VIEW"), isNull(), any()))
                .thenReturn(decision);
        when(authorizationService.dataAccessMode(decision))
                .thenReturn(LowcodeAuthorizationService.DataAccessMode.ORG);

        var result = service.list("expense", 1, 10);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(formInstanceMapper, never()).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    void submitValidationFailureDoesNotInsert() {
        setTenantUser();
        when(formDefinitionService.findLatestByFormKey("expense")).thenReturn(publishedForm());
        AuthorizationDecisionResponse decision = allowDecision("CREATE");
        when(authorizationService.requireFormDecision(eq("expense"), eq("CREATE"), isNull(), any()))
                .thenReturn(decision);
        when(authorizationService.allowsCreate(decision)).thenReturn(true);
        doThrow(new FormDataValidationException(List.of(
                new FormFieldValidationError("amount", "REQUIRED", "required", "required"))))
                .when(formDataValidator).validate(any(), any());

        assertThrows(FormDataValidationException.class,
                () -> service.submit("expense", submitRequest()));

        verify(formInstanceMapper, never()).insert(any(LcFormInstance.class));
    }

    @Test
    void submitPersistsActionMetadataWhenOwnerContextExists() throws Exception {
        setTenantUser();
        AtomicReference<LcFormInstance> instanceRef = new AtomicReference<>();
        when(formDefinitionService.findLatestByFormKey("expense")).thenReturn(publishedForm());
        AuthorizationDecisionResponse decision = allowDecision("SUBMIT");
        when(authorizationService.requireFormDecision(eq("expense"), eq("SUBMIT"), isNull(), any()))
                .thenReturn(decision);
        when(authorizationService.allowsCreate(decision)).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(formInstanceMapper.insert(any(LcFormInstance.class))).thenAnswer(invocation -> {
            instanceRef.set(invocation.getArgument(0));
            return 1;
        });
        LowcodeActionExecutionContext.set(ownerDispatchRequest());

        var response = service.submit("expense", submitRequest(), "SUBMIT");

        assertEquals("act_lowcode_001", response.getActionId());
        assertEquals("lowcode.form.submit", response.getActionType());
        assertEquals("GUI", response.getActionSource());
        assertEquals("USER", response.getActionActorType());
        assertEquals("U001", response.getActionActorId());
        assertEquals("alice", response.getActionActorName());
        assertEquals("trace-action-1", response.getActionTraceId());
        assertEquals("corr-001", response.getActionCorrelationId());
        assertEquals("act_lowcode_001", instanceRef.get().getActionId());
    }

    @Test
    void detailDeniedWhenSelfScopeDoesNotOwnInstance() {
        setTenantUser();
        LcFormInstance instance = instance("INS001", "U999");
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        AuthorizationDecisionResponse decision = allowDecision("VIEW");
        when(authorizationService.requireFormDecision(eq("expense"), eq("VIEW"), eq("INS001"), any()))
                .thenReturn(decision);
        when(authorizationService.canAccessInstance(decision, instance)).thenReturn(false);

        BizException exception = assertThrows(BizException.class, () -> service.getById("INS001"));

        assertEquals("FORM_INSTANCE_DETAIL_DENIED", exception.getMessage());
    }

    @Test
    void crossTenantDetailReturnsNotFound() {
        setTenantUser();
        when(formInstanceMapper.selectOne(any())).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> service.getById("INS001"));

        assertEquals("FORM_INSTANCE_NOT_FOUND", exception.getMessage());
    }

    @Test
    void bindProcessIsIdempotentForSameBinding() {
        setTenantUser();
        LcFormInstance instance = instance("INS001", "U001");
        instance.setProcessKey("expense_report");
        instance.setProcessInstanceId("PROC001");
        instance.setWorkflowStatus("RUNNING");
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        AuthorizationDecisionResponse decision = allowDecision("EDIT");
        when(authorizationService.requireFormDecision(eq("expense"), eq("EDIT"), eq("INS001"), any()))
                .thenReturn(decision);
        when(authorizationService.canAccessInstance(decision, instance)).thenReturn(true);
        when(authorizationService.applyReadRules(any(FormInstanceResponse.class), eq(decision)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.bindProcess("expense", "INS001", bindRequest("PROC001", "RUNNING"));

        assertEquals("PROC001", response.getProcessInstanceId());
        verify(formInstanceMapper, never()).updateById(any(LcFormInstance.class));
        verify(workflowAuditMapper, never()).insert(any(LcFormInstanceWorkflowAudit.class));
    }

    @Test
    void bindProcessRejectsDifferentProcess() {
        setTenantUser();
        LcFormInstance instance = instance("INS001", "U001");
        instance.setProcessKey("expense_report");
        instance.setProcessInstanceId("PROC000");
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        AuthorizationDecisionResponse decision = allowDecision("EDIT");
        when(authorizationService.requireFormDecision(eq("expense"), eq("EDIT"), eq("INS001"), any()))
                .thenReturn(decision);
        when(authorizationService.canAccessInstance(decision, instance)).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> service.bindProcess("expense", "INS001", bindRequest("PROC001", "RUNNING")));

        assertEquals("FORM_PROCESS_ALREADY_BOUND", exception.getMessage());
    }

    @Test
    void bindProcessRecordsWorkflowAudit() {
        setTenantUser();
        LcFormInstance instance = instance("INS001", "U001");
        AtomicReference<LcFormInstanceWorkflowAudit> auditRef = new AtomicReference<>();
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        AuthorizationDecisionResponse decision = allowDecision("EDIT");
        when(authorizationService.requireFormDecision(eq("expense"), eq("EDIT"), eq("INS001"), any()))
                .thenReturn(decision);
        when(authorizationService.canAccessInstance(decision, instance)).thenReturn(true);
        when(authorizationService.applyReadRules(any(FormInstanceResponse.class), eq(decision)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowAuditMapper.insert(any(LcFormInstanceWorkflowAudit.class))).thenAnswer(invocation -> {
            auditRef.set(invocation.getArgument(0));
            return 1;
        });
        LowcodeActionExecutionContext.set(ownerDispatchRequest());

        var response = service.bindProcess("expense", "INS001", bindRequest("PROC001", "RUNNING"));

        assertEquals("RUNNING", response.getWorkflowStatus());
        assertEquals("PROC001", response.getProcessInstanceId());
        assertEquals("act_lowcode_001", response.getActionId());
        assertEquals("BIND_PROCESS", auditRef.get().getChangeType());
        assertEquals("RUNNING", auditRef.get().getWorkflowStatus());
        assertEquals("act_lowcode_001", auditRef.get().getActionId());
        assertEquals("lowcode.form.submit", auditRef.get().getActionType());
        assertEquals("GUI", auditRef.get().getActionSource());
        assertEquals("U001", auditRef.get().getActionActorId());
        assertEquals("corr-001", auditRef.get().getActionCorrelationId());
    }

    @Test
    void updateWorkflowStatusRecordsPreviousAndNewStatus() {
        setTenantUser();
        LcFormInstance instance = instance("INS001", "U001");
        instance.setProcessKey("expense_report");
        instance.setProcessInstanceId("PROC001");
        instance.setWorkflowStatus("RUNNING");
        AtomicReference<LcFormInstanceWorkflowAudit> auditRef = new AtomicReference<>();
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        AuthorizationDecisionResponse decision = allowDecision("EDIT");
        when(authorizationService.requireFormDecision(eq("expense"), eq("EDIT"), eq("INS001"), any()))
                .thenReturn(decision);
        when(authorizationService.canAccessInstance(decision, instance)).thenReturn(true);
        when(authorizationService.applyReadRules(any(FormInstanceResponse.class), eq(decision)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowAuditMapper.insert(any(LcFormInstanceWorkflowAudit.class))).thenAnswer(invocation -> {
            auditRef.set(invocation.getArgument(0));
            return 1;
        });

        var response = service.updateWorkflowStatus("expense", "INS001", workflowStatusRequest("COMPLETED"));

        assertEquals("COMPLETED", response.getWorkflowStatus());
        assertEquals("RUNNING", auditRef.get().getPreviousWorkflowStatus());
        assertEquals("COMPLETED", auditRef.get().getWorkflowStatus());
        assertEquals("WORKFLOW_STATUS_UPDATE", auditRef.get().getChangeType());
        assertEquals("trace-1", auditRef.get().getTraceId());
    }

    @Test
    void updateEnforcesEditAuthorizationAndWritableFields() {
        setTenantUser();
        when(formDefinitionService.findLatestByFormKey("expense")).thenReturn(publishedForm());
        LcFormInstance instance = instance("INS001", "U001");
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        AuthorizationDecisionResponse decision = allowDecision("EDIT");
        when(authorizationService.requireFormDecision(eq("expense"), eq("EDIT"), eq("INS001"), any()))
                .thenReturn(decision);
        when(authorizationService.canAccessInstance(decision, instance)).thenReturn(true);
        when(authorizationService.applyReadRules(any(FormInstanceResponse.class), eq(decision)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateFormInstanceRequest request = new UpdateFormInstanceRequest();
        request.setData(Map.of("amount", 100));
        var response = service.update("expense", "INS001", request);

        assertEquals("INS001", response.getId());
        verify(formInstanceMapper).updateById(any(LcFormInstance.class));
        verify(authorizationService).requireWritableFields(eq(decision), any());
    }

    @Test
    void exportEnforcesExportAuthorization() {
        setTenantUser();
        LcFormInstance instance = instance("INS001", "U001");
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        AuthorizationDecisionResponse decision = allowDecision("EXPORT");
        when(authorizationService.requireFormDecision(eq("expense"), eq("EXPORT"), eq("INS001"), any()))
                .thenReturn(decision);
        when(authorizationService.canAccessInstance(decision, instance)).thenReturn(true);
        when(authorizationService.applyReadRules(any(FormInstanceResponse.class), eq(decision)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.export("expense", "INS001");

        assertEquals("INS001", response.getId());
    }

    @Test
    void deleteEnforcesDeleteAuthorization() {
        setTenantUser();
        LcFormInstance instance = instance("INS001", "U001");
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        AuthorizationDecisionResponse decision = allowDecision("DELETE");
        when(authorizationService.requireFormDecision(eq("expense"), eq("DELETE"), eq("INS001"), any()))
                .thenReturn(decision);
        when(authorizationService.canAccessInstance(decision, instance)).thenReturn(true);

        service.delete("expense", "INS001");

        verify(formInstanceMapper, times(1)).deleteById((java.io.Serializable) "INS001");
    }

    @Test
    void deleteDeniedWhenNotAuthorized() {
        setTenantUser();
        LcFormInstance instance = instance("INS001", "U001");
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        AuthorizationDecisionResponse decision = allowDecision("DELETE");
        when(authorizationService.requireFormDecision(eq("expense"), eq("DELETE"), eq("INS001"), any()))
                .thenReturn(decision);
        when(authorizationService.canAccessInstance(decision, instance)).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> service.delete("expense", "INS001"));

        assertEquals("FORM_INSTANCE_DELETE_DENIED", exception.getMessage());
        verify(formInstanceMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void updateDeniedWhenSelfScopeDoesNotOwnInstance() {
        setTenantUser();
        when(formDefinitionService.findLatestByFormKey("expense")).thenReturn(publishedForm());
        LcFormInstance instance = instance("INS001", "U999");
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        AuthorizationDecisionResponse decision = allowDecision("EDIT");
        when(authorizationService.requireFormDecision(eq("expense"), eq("EDIT"), eq("INS001"), any()))
                .thenReturn(decision);
        when(authorizationService.canAccessInstance(decision, instance)).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> service.update("expense", "INS001", new UpdateFormInstanceRequest()));

        assertEquals("FORM_INSTANCE_EDIT_DENIED", exception.getMessage());
        verify(formInstanceMapper, never()).updateById(any(LcFormInstance.class));
    }

    @Test
    void exportDeniedWhenNotAuthorized() {
        setTenantUser();
        LcFormInstance instance = instance("INS001", "U001");
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        AuthorizationDecisionResponse decision = allowDecision("EXPORT");
        when(authorizationService.requireFormDecision(eq("expense"), eq("EXPORT"), eq("INS001"), any()))
                .thenReturn(decision);
        when(authorizationService.canAccessInstance(decision, instance)).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> service.export("expense", "INS001"));

        assertEquals("FORM_INSTANCE_EXPORT_DENIED", exception.getMessage());
    }

    private AuthorizationDecisionResponse allowDecision(String actionCode) {
        AuthorizationDecisionResponse decision = new AuthorizationDecisionResponse();
        decision.setAllowed(true);
        decision.setTenantId("tenant-a");
        decision.setUserId("U001");
        decision.setResourceCode("LOWCODE_FORM:EXPENSE");
        decision.setActionCode(actionCode);
        return decision;
    }

    private LcFormDefinition publishedForm() {
        LcFormDefinition definition = new LcFormDefinition();
        definition.setId("FORM001");
        definition.setTenantId("tenant-a");
        definition.setFormKey("expense");
        definition.setVersion(1);
        definition.setStatus("PUBLISHED");
        definition.setSchemaHash("hash");
        definition.setSchemaJson("{\"type\":\"object\"}");
        return definition;
    }

    private SubmitFormInstanceRequest submitRequest() {
        SubmitFormInstanceRequest request = new SubmitFormInstanceRequest();
        request.setData(Map.of());
        return request;
    }

    private LcFormInstance instance(String id, String submittedBy) {
        LcFormInstance instance = new LcFormInstance();
        instance.setId(id);
        instance.setTenantId("tenant-a");
        instance.setFormDefinitionId("FORM001");
        instance.setFormDefinitionVersion(1);
        instance.setSchemaHash("hash");
        instance.setFormKey("expense");
        instance.setStatus("SUBMITTED");
        instance.setSubmittedBy(submittedBy);
        instance.setSubmittedAt(LocalDateTime.now());
        return instance;
    }

    private BindFormProcessRequest bindRequest(String processInstanceId, String workflowStatus) {
        BindFormProcessRequest request = new BindFormProcessRequest();
        request.setProcessKey("expense_report");
        request.setProcessInstanceId(processInstanceId);
        request.setWorkflowStatus(workflowStatus);
        request.setTraceId("trace-1");
        return request;
    }

    private UpdateWorkflowStatusRequest workflowStatusRequest(String workflowStatus) {
        UpdateWorkflowStatusRequest request = new UpdateWorkflowStatusRequest();
        request.setWorkflowStatus(workflowStatus);
        request.setTraceId("trace-1");
        return request;
    }

    private void setTenantUser() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", "tenant-a", List.of(), List.of(), null, null, null));
    }

    private ActionOwnerDispatchRequest ownerDispatchRequest() {
        ActionOwnerDispatchRequest request = new ActionOwnerDispatchRequest();
        request.setActionId("act_lowcode_001");
        request.setActionType("lowcode.form.submit");
        request.setSource(ActionSource.GUI);
        ActionActor actor = new ActionActor();
        actor.setType(ActionActorType.USER);
        actor.setId("U001");
        actor.setDisplayName("alice");
        actor.setTenantId("tenant-a");
        request.setActor(actor);
        ActionContext context = new ActionContext();
        context.setTenantId("tenant-a");
        context.setTraceId("trace-action-1");
        context.setCorrelationId("corr-001");
        request.setContext(context);
        return request;
    }
}
