package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.authz.AuthzDecisionReason;
import com.triobase.common.dto.authz.AuthzFieldRule;
import com.triobase.common.dto.authz.AuthzGuardRequirement;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionResponse;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.common.dto.internal.PublishedFormSnapshotResponse;
import com.triobase.service.lowcode.dto.BindFormProcessRequest;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
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
import com.triobase.service.lowcode.mapper.FormDefinitionMapper;
import com.triobase.service.lowcode.mapper.FormRelationMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    private FormRelationMapper formRelationMapper;
    @Mock
    private FormDefinitionMapper formDefinitionMapper;
    @Mock
    private FormDefinitionService formDefinitionService;
    @Mock
    private FormInstanceService formInstanceService;
    @Mock
    private ApplicationInstanceGraphService instanceGraphService;
    @Mock
    private WorkflowLaunchClient workflowLaunchClient;
    @Mock
    private LowcodeAuthorizationService authorizationService;
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
        assertEquals("{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}", response.getSchemaJson());
        assertThat(response.getPages()).hasSize(2);
        assertThat(response.getActions()).extracting("actionCode").containsExactly("save");
        assertThat(response.getFieldRules()).extracting("fieldKey").containsExactly("amount");
        assertThat(response.getActions().getFirst().getGuardRequirements()).extracting("guardCode")
                .containsExactly("DOCUMENT_STATUS");
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
        when(formInstanceService.submit(eq("expense"), any(SubmitFormInstanceRequest.class), eq("SUBMIT")))
                .thenReturn(instance("INS001", "SUBMITTED"));

        var response = service.executeLocalAction("expense_report", null, "save", actionRequest());

        assertEquals(ActionStatus.SUCCEEDED, response.getStatus());
        assertEquals("FORM_SAVED", response.getData().get("runtimeStatus"));
        assertEquals("INS001", ((FormInstanceResponse) response.getData().get("formInstance")).getId());
    }

    @Test
    void submitAndLaunchWorkflowBindsProcessOnSuccess() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET", "/api/v1/forms/*/submit:POST"));
        AtomicReference<WorkflowLaunchClient.StartCommand> commandRef = new AtomicReference<>();
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                action("submitAndLaunch", "SUBMIT_AND_LAUNCH_WORKFLOW", "/api/v1/forms/*/submit:POST", "expense_report")));
        when(formInstanceService.submit(eq("expense"), any(SubmitFormInstanceRequest.class), eq("SUBMIT")))
                .thenReturn(instance("INS001", "SUBMITTED"));
        when(workflowLaunchClient.start(any())).thenAnswer(invocation -> {
            commandRef.set(invocation.getArgument(0));
            return workflow("PROC001", "RUNNING");
        });
        when(formInstanceService.bindProcess(eq("expense"), eq("INS001"), any(BindFormProcessRequest.class)))
                .thenReturn(instance("INS001", "RUNNING"));

        var response = service.executeLocalAction("expense_report", null, "submitAndLaunch", actionRequest());

        assertEquals(ActionStatus.SUCCEEDED, response.getStatus());
        assertEquals("WORKFLOW_STARTED", response.getData().get("runtimeStatus"));
        assertEquals("PROC001", ((RuntimeWorkflowResponse) response.getData().get("workflow")).getProcessInstanceId());
        assertEquals("lowcode:expense_report:1:submitAndLaunch:INS001", commandRef.get().idempotencyKey());
    }

    @Test
    void workflowLaunchFailureLeavesPendingRetryableInstance() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET", "/api/v1/forms/*/submit:POST"));
        FormInstanceResponse pending = instance("INS001", "PENDING_WORKFLOW");
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                action("submitAndLaunch", "SUBMIT_AND_LAUNCH_WORKFLOW", "/api/v1/forms/*/submit:POST", "expense_report")));
        when(formInstanceService.submit(eq("expense"), any(SubmitFormInstanceRequest.class), eq("SUBMIT")))
                .thenReturn(instance("INS001", "SUBMITTED"));
        when(workflowLaunchClient.start(any()))
                .thenThrow(new WorkflowLaunchException("WORKFLOW_START_UNAVAILABLE", "down", false));
        when(formInstanceService.getById("INS001")).thenReturn(pending);

        var response = service.executeLocalAction("expense_report", null, "submitAndLaunch", actionRequest());

        assertEquals(ActionStatus.FAILED, response.getStatus());
        assertEquals("WORKFLOW_PENDING", response.getData().get("runtimeStatus"));
        assertThat(response.isRetryable()).isTrue();
        verify(formInstanceService).updateWorkflowStatus(eq("expense"), eq("INS001"), any(UpdateWorkflowStatusRequest.class));
    }

    @Test
    void invalidSchemaRejectsActionBeforeWorkflowLaunch() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET", "/api/v1/forms/*/submit:POST"));
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                action("submitAndLaunch", "SUBMIT_AND_LAUNCH_WORKFLOW", "/api/v1/forms/*/submit:POST", "expense_report")));
        when(formInstanceService.submit(eq("expense"), any(SubmitFormInstanceRequest.class), eq("SUBMIT")))
                .thenThrow(new BizException(40055, "FORM_INSTANCE_SCHEMA_INVALID"));

        BizException exception = assertThrows(BizException.class,
                () -> service.executeLocalAction("expense_report", null, "submitAndLaunch", actionRequest()));

        assertEquals("FORM_INSTANCE_SCHEMA_INVALID", exception.getMessage());
        verify(workflowLaunchClient, never()).start(any());
        verify(formInstanceService, never()).bindProcess(anyString(), anyString(), any());
    }

    @Test
    void explicitAuthorizationDenyPreventsRuntimeActionExecution() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET"));
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                action("save", "SUBMIT", "/api/v1/forms/*/submit:POST", null)));
        when(authorizationService.decideForm(eq("expense"), eq("SUBMIT"), any(), any()))
                .thenReturn(denyDecision("LOWCODE_FORM:EXPENSE", "SUBMIT"));

        BizException exception = assertThrows(BizException.class,
                () -> service.executeLocalAction("expense_report", null, "save", actionRequest()));

        assertEquals("APPLICATION_RUNTIME_ACTION_NOT_FOUND", exception.getMessage());
        verify(formInstanceService, never()).submit(anyString(), any(), anyString());
    }

    @Test
    void retryWorkflowBindsProcessOnSuccessAndKeepsActionIdempotency() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET", "/api/v1/forms/*/submit:POST"));
        AtomicReference<WorkflowLaunchClient.StartCommand> commandRef = new AtomicReference<>();
        FormInstanceResponse pending = instance("INS001", "PENDING_WORKFLOW");
        pending.setDataJson("{\"amount\":12}");
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                action("submitAndLaunch", "SUBMIT_AND_LAUNCH_WORKFLOW", "/api/v1/forms/*/submit:POST", "expense_report")));
        when(formInstanceService.getById("INS001")).thenReturn(pending);
        when(workflowLaunchClient.start(any())).thenAnswer(invocation -> {
            commandRef.set(invocation.getArgument(0));
            return workflow("PROC002", "RUNNING");
        });
        when(formInstanceService.bindProcess(eq("expense"), eq("INS001"), any(BindFormProcessRequest.class)))
                .thenReturn(instance("INS001", "RUNNING"));
        RuntimeRetryWorkflowRequest retry = new RuntimeRetryWorkflowRequest();
        retry.setIdempotencyKey("retry-idem-001");

        var response = service.executeLocalWorkflowRetry(
                "expense_report", null, "INS001", retry, actionRequest("retry-idem-001"));

        assertEquals(ActionStatus.SUCCEEDED, response.getStatus());
        assertEquals("WORKFLOW_STARTED", response.getData().get("runtimeStatus"));
        assertEquals("retry-idem-001", commandRef.get().idempotencyKey());
        assertEquals("act-test-001", commandRef.get().actionId());
        assertEquals("lowcode.form.submit", commandRef.get().actionType());
        assertEquals("LUI", commandRef.get().actionSource());
        assertEquals("USER", commandRef.get().actionActorType());
        assertEquals("U001", commandRef.get().actionActorId());
        assertEquals("trace-test-001", commandRef.get().actionTraceId());
        assertEquals("run-test-001", commandRef.get().actionCorrelationId());
        assertEquals("PROC002", ((RuntimeWorkflowResponse) response.getData().get("workflow")).getProcessInstanceId());
    }

    @Test
    void workflowLaunchUsesGlobalActionIdempotencyKeyBeforeFallbackKey() {
        setRuntimeUser(List.of("/api/v1/forms/expense/instances:GET", "/api/v1/forms/*/submit:POST"));
        AtomicReference<WorkflowLaunchClient.StartCommand> commandRef = new AtomicReference<>();
        when(applicationVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                action("submitAndLaunch", "SUBMIT_AND_LAUNCH_WORKFLOW", "/api/v1/forms/*/submit:POST", "expense_report")));
        when(formInstanceService.submit(eq("expense"), any(SubmitFormInstanceRequest.class), eq("SUBMIT")))
                .thenReturn(instance("INS001", "SUBMITTED"));
        when(workflowLaunchClient.start(any())).thenAnswer(invocation -> {
            commandRef.set(invocation.getArgument(0));
            return workflow("PROC001", "RUNNING");
        });
        when(formInstanceService.bindProcess(eq("expense"), eq("INS001"), any(BindFormProcessRequest.class)))
                .thenReturn(instance("INS001", "RUNNING"));

        service.executeLocalAction("expense_report", null, "submitAndLaunch", actionRequest("global-idem-001"));

        assertEquals("global-idem-001", commandRef.get().idempotencyKey());
        assertEquals("act-test-001", commandRef.get().actionId());
        assertEquals("run-test-001", commandRef.get().actionCorrelationId());
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
                () -> service.executeLocalWorkflowRetry("expense_report", null, "INS001", new RuntimeRetryWorkflowRequest()));

        assertEquals("APPLICATION_WORKFLOW_VERSION_CONFLICT", exception.getMessage());
    }

    private void setRuntimeUser(List<String> permissions) {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", "tenant-a", List.of(), permissions, null, null, null));
        lenient().when(authorizationService.appResourceCode(anyString()))
                .thenAnswer(invocation -> "LOWCODE_APP:" + invocation.getArgument(0, String.class).toUpperCase());
        lenient().when(authorizationService.formResourceCode(anyString()))
                .thenAnswer(invocation -> "LOWCODE_FORM:" + invocation.getArgument(0, String.class).toUpperCase());
        lenient().when(authorizationService.decideResource(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> allowDecision(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        !invocation.getArgument(0, String.class).contains("SECRET")));
        lenient().when(authorizationService.decideForm(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> allowDecision(
                        "LOWCODE_FORM:" + invocation.getArgument(0, String.class).toUpperCase(),
                        invocation.getArgument(1),
                        true));
        lenient().when(authorizationService.decisionRequest(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> decisionRequest(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)));
        lenient().when(authorizationService.batchDecide(any()))
                .thenAnswer(invocation -> batchResponse(invocation.getArgument(0)));
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
        response.setSchemaJson("{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}");
        response.setUiSchemaJson("{}");
        return response;
    }

    private AuthorizationDecisionRequest decisionRequest(String resourceCode, String actionCode, String businessObjectId) {
        AuthorizationDecisionRequest request = new AuthorizationDecisionRequest();
        request.setResourceCode(resourceCode);
        request.setActionCode(actionCode);
        request.setBusinessObjectId(businessObjectId);
        return request;
    }

    private AuthorizationBatchDecisionResponse batchResponse(List<AuthorizationDecisionRequest> requests) {
        AuthorizationBatchDecisionResponse response = new AuthorizationBatchDecisionResponse();
        response.setDecisions(requests.stream()
                .map(request -> {
                    boolean allowed = !"VIEW".equals(request.getActionCode()) || request.getBusinessObjectId() != null;
                    AuthorizationDecisionResponse decision = allowDecision(
                            request.getResourceCode(), request.getActionCode(), allowed);
                    if ("FORM001".equals(request.getBusinessObjectId())) {
                        decision.setFieldRules(List.of(fieldRule("amount")));
                    }
                    if ("SUBMIT".equals(request.getActionCode())) {
                        decision.setGuardRequirements(List.of(guard("DOCUMENT_STATUS")));
                    }
                    return decision;
                })
                .toList());
        return response;
    }

    private AuthorizationDecisionResponse allowDecision(String resourceCode, String actionCode, boolean allowed) {
        AuthorizationDecisionResponse decision = new AuthorizationDecisionResponse();
        decision.setTenantId("tenant-a");
        decision.setUserId("U001");
        decision.setResourceCode(resourceCode);
        decision.setActionCode(actionCode);
        decision.setAllowed(allowed);
        return decision;
    }

    private AuthorizationDecisionResponse denyDecision(String resourceCode, String actionCode) {
        AuthorizationDecisionResponse decision = allowDecision(resourceCode, actionCode, false);
        AuthzDecisionReason reason = new AuthzDecisionReason();
        reason.setCode("AUTHZ_DENY_GRANT_MATCHED");
        reason.setMessage("explicit deny");
        decision.setReasons(List.of(reason));
        return decision;
    }

    private AuthzFieldRule fieldRule(String fieldKey) {
        AuthzFieldRule rule = new AuthzFieldRule();
        rule.setFieldKey(fieldKey);
        rule.setReadMode("MASKED");
        rule.setWriteMode("READ_ONLY");
        rule.setMaskStrategy("LAST4");
        return rule;
    }

    private AuthzGuardRequirement guard(String guardCode) {
        AuthzGuardRequirement requirement = new AuthzGuardRequirement();
        requirement.setGuardCode(guardCode);
        requirement.setOwnerService("service-lowcode");
        requirement.setDescription("status guard");
        return requirement;
    }

    private GlobalActionRequest actionRequest() {
        return actionRequest(null);
    }

    private GlobalActionRequest actionRequest(String idempotencyKey) {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionId("act-test-001");
        request.setActionType("lowcode.form.submit");
        request.setSource(ActionSource.LUI);
        ActionActor actor = new ActionActor();
        actor.setType(ActionActorType.USER);
        actor.setId("U001");
        actor.setDisplayName("Alice");
        request.setActor(actor);
        ActionContext context = new ActionContext();
        context.setTraceId("trace-test-001");
        context.setCorrelationId("run-test-001");
        request.setContext(context);
        request.setIdempotencyKey(idempotencyKey);
        request.setPayload(Map.of("data", Map.of("amount", 12)));
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
