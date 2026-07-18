package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.internal.PublishedFormSnapshotResponse;
import com.triobase.service.lowcode.dto.BindFormProcessRequest;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.dto.RuntimeActionRequest;
import com.triobase.service.lowcode.dto.RuntimeRetryWorkflowRequest;
import com.triobase.service.lowcode.dto.RuntimeWorkflowResponse;
import com.triobase.service.lowcode.dto.SubmitFormInstanceRequest;
import com.triobase.service.lowcode.dto.UpdateWorkflowStatusRequest;
import com.triobase.service.lowcode.entity.LcApplication;
import com.triobase.service.lowcode.entity.LcApplicationAction;
import com.triobase.service.lowcode.entity.LcApplicationPage;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import com.triobase.service.lowcode.mapper.ApplicationActionMapper;
import com.triobase.service.lowcode.mapper.ApplicationMapper;
import com.triobase.service.lowcode.mapper.ApplicationPageMapper;
import com.triobase.service.lowcode.mapper.ApplicationVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationRuntimeServiceTest {

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationVersionMapper applicationVersionMapper;
    @Mock
    private ApplicationPageMapper applicationPageMapper;
    @Mock
    private ApplicationActionMapper applicationActionMapper;
    @Mock
    private FormDefinitionService formDefinitionService;
    @Mock
    private FormInstanceService formInstanceService;
    @Mock
    private WorkflowLaunchClient workflowLaunchClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ApplicationRuntimeService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void listAvailableHidesAppsWithoutViewPermission() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET"));
        LcApplicationVersion visible = version("APPV001", "expense_report",
                "/api/v1/forms/expense/instances:GET");
        LcApplicationVersion hidden = version("APPV002", "secret_app",
                "/api/v1/forms/secret/instances:GET");
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(hidden, visible));
        when(applicationMapper.selectById("APP001")).thenReturn(application("APP001", "expense_report"));

        var result = service.listAvailable(1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("expense_report", result.getRecords().getFirst().getAppKey());
    }

    @Test
    void descriptorReturnsPublishedFormSnapshotAndPermissionFilteredActions() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET", "/api/v1/forms/*/submit:POST"));
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationMapper.selectById("APP001")).thenReturn(application("APP001", "expense_report"));
        when(formDefinitionService.getPublishedSnapshot("FORM001")).thenReturn(snapshot());
        when(applicationPageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(page("LIST"), page("DETAIL")));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                action("save", "SUBMIT", "/api/v1/forms/*/submit:POST", null),
                action("hidden", "OPEN_DETAIL", "/api/v1/forms/secret:GET", null)));

        var response = service.descriptor("expense_report", null);

        assertEquals("expense_report", response.getAppKey());
        assertEquals("{\"type\":\"object\"}", response.getSchemaJson());
        assertThat(response.getPages()).hasSize(2);
        assertThat(response.getActions()).extracting("actionCode").containsExactly("save");
    }

    @Test
    void listInstancesRequiresListPageAndDelegatesToFormRuntime() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET"));
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationPageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(page("LIST")));
        when(formInstanceService.list("expense", 2, 5))
                .thenReturn(PageResult.of(List.of(instance("INS001", "SUBMITTED")), 1, 2, 5));

        var result = service.listInstances("expense_report", null, 2, 5);

        assertEquals(1, result.getTotal());
        verify(formInstanceService).list("expense", 2, 5);
    }

    @Test
    void detailRejectsInstanceFromDifferentForm() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET"));
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        FormInstanceResponse instance = instance("INS001", "SUBMITTED");
        instance.setFormKey("other");
        when(formInstanceService.getById("INS001")).thenReturn(instance);

        BizException exception = assertThrows(BizException.class,
                () -> service.getInstance("expense_report", null, "INS001"));

        assertEquals("APPLICATION_RUNTIME_INSTANCE_NOT_FOUND", exception.getMessage());
    }

    @Test
    void createAndSaveActionSubmitsFormOnly() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET", "/api/v1/forms/*/submit:POST"));
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                action("save", "SUBMIT", "/api/v1/forms/*/submit:POST", null)));
        when(formInstanceService.submit(eq("expense"), any(SubmitFormInstanceRequest.class)))
                .thenReturn(instance("INS001", "SUBMITTED"));

        var response = service.runAction("expense_report", null, "save", actionRequest());

        assertEquals("FORM_SAVED", response.getStatus());
        assertEquals("INS001", response.getFormInstance().getId());
    }

    @Test
    void submitAndLaunchWorkflowBindsProcessOnSuccess() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET", "/api/v1/forms/*/submit:POST"));
        AtomicReference<WorkflowLaunchClient.StartCommand> commandRef = new AtomicReference<>();
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                action("submitAndLaunch", "SUBMIT_AND_LAUNCH_WORKFLOW", "/api/v1/forms/*/submit:POST", "expense_report")));
        when(formInstanceService.submit(eq("expense"), any(SubmitFormInstanceRequest.class)))
                .thenReturn(instance("INS001", "SUBMITTED"));
        when(workflowLaunchClient.start(any())).thenAnswer(invocation -> {
            commandRef.set(invocation.getArgument(0));
            return workflow("PROC001", "RUNNING");
        });
        when(formInstanceService.bindProcess(eq("expense"), eq("INS001"), any(BindFormProcessRequest.class)))
                .thenReturn(instance("INS001", "RUNNING"));

        var response = service.runAction("expense_report", null, "submitAndLaunch", actionRequest());

        assertEquals("WORKFLOW_STARTED", response.getStatus());
        assertEquals("PROC001", response.getWorkflow().getProcessInstanceId());
        assertEquals("lowcode:expense_report:1:submitAndLaunch:INS001", commandRef.get().idempotencyKey());
    }

    @Test
    void workflowLaunchFailureLeavesPendingRetryableInstance() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET", "/api/v1/forms/*/submit:POST"));
        FormInstanceResponse pending = instance("INS001", "PENDING_WORKFLOW");
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                action("submitAndLaunch", "SUBMIT_AND_LAUNCH_WORKFLOW", "/api/v1/forms/*/submit:POST", "expense_report")));
        when(formInstanceService.submit(eq("expense"), any(SubmitFormInstanceRequest.class)))
                .thenReturn(instance("INS001", "SUBMITTED"));
        when(workflowLaunchClient.start(any()))
                .thenThrow(new WorkflowLaunchException("WORKFLOW_START_UNAVAILABLE", "down", false));
        when(formInstanceService.getById("INS001")).thenReturn(pending);

        var response = service.runAction("expense_report", null, "submitAndLaunch", actionRequest());

        assertEquals("WORKFLOW_PENDING", response.getStatus());
        assertThat(response.isRetryable()).isTrue();
        verify(formInstanceService).updateWorkflowStatus(eq("expense"), eq("INS001"), any(UpdateWorkflowStatusRequest.class));
    }

    @Test
    void retryWorkflowSurfacesStaleWorkflowVersionConflict() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET", "/api/v1/forms/*/submit:POST"));
        FormInstanceResponse pending = instance("INS001", "PENDING_WORKFLOW");
        pending.setDataJson("{\"amount\":12}");
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                action("submitAndLaunch", "SUBMIT_AND_LAUNCH_WORKFLOW", "/api/v1/forms/*/submit:POST", "expense_report")));
        when(formInstanceService.getById("INS001")).thenReturn(pending);
        when(workflowLaunchClient.start(any()))
                .thenThrow(new WorkflowLaunchException("40900", "PROCESS_VERSION_CONFLICT", true));

        BizException exception = assertThrows(BizException.class,
                () -> service.retryWorkflow("expense_report", null, "INS001", new RuntimeRetryWorkflowRequest()));

        assertEquals("APPLICATION_WORKFLOW_VERSION_CONFLICT", exception.getMessage());
    }

    private void setRuntimeUser(List<String> permissions) {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", "tenant-a", List.of(), permissions, null, null, null));
    }

    private LcApplicationVersion version() {
        return version("APPV001", "expense_report", "/api/v1/forms/expense/instances:GET");
    }

    private LcApplicationVersion version(String id, String appKey, String viewPermission) {
        LcApplicationVersion version = new LcApplicationVersion();
        version.setId(id);
        version.setTenantId("tenant-a");
        version.setApplicationId("APP001");
        version.setAppKey(appKey);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setName("Expense Report");
        version.setPrimaryFormDefinitionId("FORM001");
        version.setFormKey("expense");
        version.setFormVersion(1);
        version.setSchemaHash("hash");
        version.setMetadataHash("meta-hash");
        version.setViewPermissionCode(viewPermission);
        version.setPublishedAt(LocalDateTime.now());
        return version;
    }

    private LcApplication application(String id, String appKey) {
        LcApplication application = new LcApplication();
        application.setId(id);
        application.setTenantId("tenant-a");
        application.setAppKey(appKey);
        application.setName("Expense Report");
        return application;
    }

    private LcApplicationPage page(String pageType) {
        LcApplicationPage page = new LcApplicationPage();
        page.setId("PAGE-" + pageType);
        page.setApplicationVersionId("APPV001");
        page.setPageType(pageType);
        page.setMetadataJson("{}");
        page.setSortOrder(1);
        return page;
    }

    private LcApplicationAction action(String code, String type, String permission, String processKey) {
        LcApplicationAction action = new LcApplicationAction();
        action.setId("ACT-" + code);
        action.setApplicationVersionId("APPV001");
        action.setActionCode(code);
        action.setActionType(type);
        action.setLabel(code);
        action.setPermissionCode(permission);
        action.setProcessKey(processKey);
        action.setMetadataJson("{}");
        action.setStatus("ENABLED");
        action.setSortOrder(1);
        return action;
    }

    private PublishedFormSnapshotResponse snapshot() {
        PublishedFormSnapshotResponse response = new PublishedFormSnapshotResponse();
        response.setFormDefinitionId("FORM001");
        response.setFormKey("expense");
        response.setVersion(1);
        response.setSchemaHash("hash");
        response.setSchemaJson("{\"type\":\"object\"}");
        response.setUiSchemaJson("{}");
        return response;
    }

    private RuntimeActionRequest actionRequest() {
        RuntimeActionRequest request = new RuntimeActionRequest();
        request.setData(Map.of("amount", 12));
        return request;
    }

    private FormInstanceResponse instance(String id, String workflowStatus) {
        FormInstanceResponse response = new FormInstanceResponse();
        response.setId(id);
        response.setTenantId("tenant-a");
        response.setFormKey("expense");
        response.setFormDefinitionId("FORM001");
        response.setFormDefinitionVersion(1);
        response.setStatus("SUBMITTED");
        response.setWorkflowStatus(workflowStatus);
        response.setDataJson("{\"amount\":12}");
        return response;
    }

    private RuntimeWorkflowResponse workflow(String id, String status) {
        RuntimeWorkflowResponse response = new RuntimeWorkflowResponse();
        response.setProcessInstanceId(id);
        response.setProcessKey("expense_report");
        response.setVersion(1);
        response.setStatus(status);
        return response;
    }
}
