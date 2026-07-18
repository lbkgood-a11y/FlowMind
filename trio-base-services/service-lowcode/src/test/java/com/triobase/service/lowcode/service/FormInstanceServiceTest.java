package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.auth.DataScope.Dimension;
import com.triobase.common.core.auth.DataScope.Policy;
import com.triobase.common.core.auth.DataScopeProvider;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.lowcode.dto.BindFormProcessRequest;
import com.triobase.service.lowcode.dto.FormFieldValidationError;
import com.triobase.service.lowcode.dto.SubmitFormInstanceRequest;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    private DataScopeProvider dataScopeProvider;
    @Mock
    private LowcodeFormDataValidator formDataValidator;

    @InjectMocks
    private FormInstanceService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void listUsesDatabasePagination() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", "tenant-a", List.of(), List.of(), null, null, null));
        when(dataScopeProvider.resolve(eq("U001"), eq("FORM:EXPENSE"), eq("QUERY")))
                .thenReturn(allowAllScope());
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
    void submitValidationFailureDoesNotInsert() {
        setTenantUser();
        when(formDefinitionService.findLatestByFormKey("expense")).thenReturn(publishedForm());
        when(dataScopeProvider.resolve(eq("U001"), eq("FORM:EXPENSE"), eq("CREATE")))
                .thenReturn(allowAllScope("CREATE"));
        doThrow(new FormDataValidationException(List.of(
                new FormFieldValidationError("amount", "REQUIRED", "required", "required"))))
                .when(formDataValidator).validate(any(), any());

        assertThrows(FormDataValidationException.class,
                () -> service.submit("expense", submitRequest()));

        verify(formInstanceMapper, never()).insert(any(LcFormInstance.class));
    }

    @Test
    void detailDeniedWhenSelfScopeDoesNotOwnInstance() {
        setTenantUser();
        LcFormInstance instance = instance("INS001", "U999");
        when(formInstanceMapper.selectOne(any())).thenReturn(instance);
        when(dataScopeProvider.resolve(eq("U001"), eq("FORM:EXPENSE"), eq("QUERY")))
                .thenReturn(allowSelfScope());

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
        when(dataScopeProvider.resolve(eq("U001"), eq("FORM:EXPENSE"), eq("QUERY")))
                .thenReturn(allowAllScope("QUERY"));

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
        when(dataScopeProvider.resolve(eq("U001"), eq("FORM:EXPENSE"), eq("QUERY")))
                .thenReturn(allowAllScope("QUERY"));

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
        when(dataScopeProvider.resolve(eq("U001"), eq("FORM:EXPENSE"), eq("QUERY")))
                .thenReturn(allowAllScope("QUERY"));
        when(workflowAuditMapper.insert(any(LcFormInstanceWorkflowAudit.class))).thenAnswer(invocation -> {
            auditRef.set(invocation.getArgument(0));
            return 1;
        });

        var response = service.bindProcess("expense", "INS001", bindRequest("PROC001", "RUNNING"));

        assertEquals("RUNNING", response.getWorkflowStatus());
        assertEquals("PROC001", response.getProcessInstanceId());
        assertEquals("BIND_PROCESS", auditRef.get().getChangeType());
        assertEquals("RUNNING", auditRef.get().getWorkflowStatus());
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
        when(dataScopeProvider.resolve(eq("U001"), eq("FORM:EXPENSE"), eq("QUERY")))
                .thenReturn(allowAllScope("QUERY"));
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

    private DataScope allowAllScope() {
        return allowAllScope("QUERY");
    }

    private DataScope allowAllScope(String actionCode) {
        return new DataScope("U001", "FORM:EXPENSE", actionCode, false, true, List.of(),
                List.of(new Policy("R001", "ALLOW", "AND",
                        List.of(new Dimension("CREATOR", "ALL", List.of())))));
    }

    private DataScope allowSelfScope() {
        return new DataScope("U001", "FORM:EXPENSE", "QUERY", false, true, List.of(),
                List.of(new Policy("R001", "ALLOW", "AND",
                        List.of(new Dimension("CREATOR", "SELF", List.of())))));
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
}
