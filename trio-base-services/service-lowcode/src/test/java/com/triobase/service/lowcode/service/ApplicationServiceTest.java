package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.lowcode.dto.ApplicationActionRequest;
import com.triobase.service.lowcode.dto.ApplicationPageRequest;
import com.triobase.service.lowcode.dto.CreateApplicationRequest;
import com.triobase.service.lowcode.entity.LcApplication;
import com.triobase.service.lowcode.entity.LcApplicationAction;
import com.triobase.service.lowcode.entity.LcApplicationPage;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import com.triobase.service.lowcode.entity.LcFormDefinition;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
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
class ApplicationServiceTest {

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationVersionMapper applicationVersionMapper;
    @Mock
    private ApplicationPageMapper applicationPageMapper;
    @Mock
    private ApplicationActionMapper applicationActionMapper;
    @Mock
    private FormDefinitionMapper formDefinitionMapper;
    @Mock
    private FormRelationMapper formRelationMapper;
    @Mock
    private ApplicationMetadataValidator metadataValidator;
    @Mock
    private FormRelationGraphValidator relationGraphValidator;
    @Mock
    private ApplicationReferenceValidator referenceValidator;
    @Mock
    private AuthorizationResourceSyncClient authorizationResourceSyncClient;

    @InjectMocks
    private ApplicationService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void createApplicationDraftPersistsVersionPagesAndActions() {
        setTenantUser();
        AtomicReference<LcApplication> insertedApplication = new AtomicReference<>();
        AtomicReference<LcApplicationVersion> insertedVersion = new AtomicReference<>();
        List<LcApplicationPage> insertedPages = new ArrayList<>();
        List<LcApplicationAction> insertedActions = new ArrayList<>();
        when(applicationMapper.selectCount(any())).thenReturn(0L);
        when(formDefinitionMapper.selectOne(any())).thenReturn(publishedForm());
        when(applicationMapper.insert(any(LcApplication.class))).thenAnswer(invocation -> {
            insertedApplication.set(invocation.getArgument(0));
            return 1;
        });
        when(applicationVersionMapper.insert(any(LcApplicationVersion.class))).thenAnswer(invocation -> {
            insertedVersion.set(invocation.getArgument(0));
            return 1;
        });
        when(applicationPageMapper.insert(any(LcApplicationPage.class))).thenAnswer(invocation -> {
            insertedPages.add(invocation.getArgument(0));
            return 1;
        });
        when(applicationActionMapper.insert(any(LcApplicationAction.class))).thenAnswer(invocation -> {
            insertedActions.add(invocation.getArgument(0));
            return 1;
        });
        when(applicationVersionMapper.selectOne(any())).thenAnswer(invocation -> insertedVersion.get());
        when(applicationMapper.selectById(any())).thenAnswer(invocation -> insertedApplication.get());
        when(applicationPageMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> insertedPages);
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> insertedActions);

        var response = service.create(createRequest(), "alice");

        assertThat(response.getAppKey()).isEqualTo("expense_report");
        assertThat(response.getStatus()).isEqualTo("DRAFT");
        assertThat(response.getPages()).hasSize(2);
        assertThat(response.getActions()).hasSize(1);
        assertThat(insertedApplication.get().getTenantId()).isEqualTo("tenant-a");
        assertThat(insertedVersion.get().getPrimaryFormDefinitionId()).isEqualTo("FORM001");
        verify(metadataValidator).validateDraft(any(), any());
    }

    @Test
    void publishRejectsDraftFormReference() {
        setTenantUser();
        when(applicationVersionMapper.selectOne(any())).thenReturn(draftVersion());
        when(formDefinitionMapper.selectOne(any())).thenReturn(form("DRAFT"));

        BizException exception = assertThrows(BizException.class, () -> service.publish("APPV001"));

        assertEquals("APPLICATION_FORM_NOT_PUBLISHED", exception.getMessage());
    }

    @Test
    void publishValidatesFieldReferencesAndRecordsMetadataHash() {
        setTenantUser();
        LcApplicationVersion version = draftVersion();
        LcApplication application = application();
        when(applicationVersionMapper.selectOne(any())).thenReturn(version);
        when(formDefinitionMapper.selectOne(any())).thenReturn(publishedForm());
        when(applicationPageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(listPage(), detailPage()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(submitAction()));
        when(applicationMapper.selectById("APP001")).thenReturn(application);

        var response = service.publish("APPV001");

        assertThat(response.getStatus()).isEqualTo("PUBLISHED");
        assertThat(version.getMetadataHash()).isNotBlank();
        assertThat(application.getLatestPublishedVersionId()).isEqualTo("APPV001");
        verify(metadataValidator).validateDraft(any(), any());
        verify(metadataValidator).validateFieldReferences(any(), any());
        verify(referenceValidator).validatePublication(any(), any());
        verify(authorizationResourceSyncClient).syncPublishedApplication(eq(version), any(), any());
    }

    @Test
    void publishPropagatesReferenceValidationFailure() {
        setTenantUser();
        when(applicationVersionMapper.selectOne(any())).thenReturn(draftVersion());
        when(formDefinitionMapper.selectOne(any())).thenReturn(publishedForm());
        when(applicationPageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(listPage()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(submitAction()));
        doThrow(new BizException(40050, "APPLICATION_PERMISSION_NOT_REGISTERED"))
                .when(referenceValidator).validatePublication(any(), any());

        BizException exception = assertThrows(BizException.class, () -> service.publish("APPV001"));

        assertEquals("APPLICATION_PERMISSION_NOT_REGISTERED", exception.getMessage());
    }

    @Test
    void offlinePublishedApplicationUpdatesStatusAndSyncsAuth() {
        setTenantUser();
        LcApplicationVersion version = publishedVersion();
        LcApplication application = application();
        application.setStatus("PUBLISHED");
        when(applicationVersionMapper.selectOne(any())).thenReturn(version);
        when(applicationMapper.selectById("APP001")).thenReturn(application);

        var response = service.offline("APPV001");

        assertThat(response.getStatus()).isEqualTo("OFFLINE");
        verify(applicationVersionMapper).updateById(version);
        verify(authorizationResourceSyncClient).syncOfflineApplication(eq(version));
    }

    @Test
    void offlineContinuesOnSyncFailureAsBestEffort() {
        setTenantUser();
        LcApplicationVersion version = publishedVersion();
        LcApplication application = application();
        application.setStatus("PUBLISHED");
        when(applicationVersionMapper.selectOne(any())).thenReturn(version);
        when(applicationMapper.selectById("APP001")).thenReturn(application);
        doThrow(new RuntimeException("Auth service unreachable"))
                .when(authorizationResourceSyncClient).syncOfflineApplication(any());

        var response = service.offline("APPV001");

        assertThat(response.getStatus()).isEqualTo("OFFLINE");
        verify(applicationVersionMapper).updateById(version);
    }

    @Test
    void offlineRejectsNonPublishedVersion() {
        setTenantUser();
        when(applicationVersionMapper.selectOne(any())).thenReturn(draftVersion());

        BizException exception = assertThrows(BizException.class, () -> service.offline("APPV001"));

        assertEquals("ONLY_PUBLISHED_APPLICATION_CAN_BE_OFFLINE", exception.getMessage());
    }

    @Test
    void publishPropagatesAuthorizationSyncFailureWithoutPublishingVersion() {
        setTenantUser();
        LcApplicationVersion version = draftVersion();
        when(applicationVersionMapper.selectOne(any())).thenReturn(version);
        when(formDefinitionMapper.selectOne(any())).thenReturn(publishedForm());
        when(applicationPageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(listPage()));
        when(applicationActionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(submitAction()));
        doThrow(new BizException(50290, "LOWCODE_AUTHZ_SYNC_FAILED"))
                .when(authorizationResourceSyncClient).syncPublishedApplication(any(), any(), any());

        BizException exception = assertThrows(BizException.class, () -> service.publish("APPV001"));

        assertEquals("LOWCODE_AUTHZ_SYNC_FAILED", exception.getMessage());
        verify(applicationVersionMapper, never()).updateById(any(LcApplicationVersion.class));
        verify(applicationMapper, never()).updateById(any(LcApplication.class));
    }

    private CreateApplicationRequest createRequest() {
        CreateApplicationRequest request = new CreateApplicationRequest();
        request.setAppKey("expense_report");
        request.setName("Expense Report");
        request.setPrimaryFormDefinitionId("FORM001");
        request.setViewPermissionCode("/api/v1/forms/expense/instances:GET");
        request.setPages(List.of(pageRequest("LIST"), pageRequest("DETAIL")));
        request.setActions(List.of(actionRequest()));
        return request;
    }

    private ApplicationPageRequest pageRequest(String type) {
        ApplicationPageRequest request = new ApplicationPageRequest();
        request.setPageType(type);
        request.setMetadataJson("LIST".equals(type)
                ? "{\"columns\":[{\"fieldKey\":\"amount\",\"label\":\"Amount\"}]}"
                : "{\"sections\":[{\"fields\":[{\"fieldKey\":\"amount\"}]}]}");
        return request;
    }

    private ApplicationActionRequest actionRequest() {
        ApplicationActionRequest request = new ApplicationActionRequest();
        request.setActionCode("submit");
        request.setActionType("SUBMIT");
        request.setLabel("Submit");
        request.setPermissionCode("/api/v1/forms/*/submit:POST");
        request.setMetadataJson("{}");
        return request;
    }

    private LcApplication application() {
        LcApplication application = new LcApplication();
        application.setId("APP001");
        application.setTenantId("tenant-a");
        application.setAppKey("expense_report");
        application.setName("Expense Report");
        application.setStatus("DRAFT");
        application.setLatestVersion(1);
        return application;
    }

    private LcApplicationVersion publishedVersion() {
        LcApplicationVersion version = draftVersion();
        version.setStatus("PUBLISHED");
        version.setPublishedAt(java.time.LocalDateTime.now());
        return version;
    }

    private LcApplicationVersion draftVersion() {
        LcApplicationVersion version = new LcApplicationVersion();
        version.setId("APPV001");
        version.setTenantId("tenant-a");
        version.setApplicationId("APP001");
        version.setAppKey("expense_report");
        version.setVersion(1);
        version.setStatus("DRAFT");
        version.setName("Expense Report");
        version.setPrimaryFormDefinitionId("FORM001");
        version.setFormKey("expense");
        version.setFormVersion(1);
        version.setSchemaHash("hash");
        return version;
    }

    private LcApplicationPage listPage() {
        LcApplicationPage page = new LcApplicationPage();
        page.setId("PAGE001");
        page.setApplicationVersionId("APPV001");
        page.setPageType("LIST");
        page.setMetadataJson("{\"columns\":[{\"fieldKey\":\"amount\",\"label\":\"Amount\"}]}");
        page.setSortOrder(0);
        return page;
    }

    private LcApplicationPage detailPage() {
        LcApplicationPage page = new LcApplicationPage();
        page.setId("PAGE002");
        page.setApplicationVersionId("APPV001");
        page.setPageType("DETAIL");
        page.setMetadataJson("{\"sections\":[{\"fields\":[{\"fieldKey\":\"amount\"}]}]}");
        page.setSortOrder(1);
        return page;
    }

    private LcApplicationAction submitAction() {
        LcApplicationAction action = new LcApplicationAction();
        action.setId("ACT001");
        action.setApplicationVersionId("APPV001");
        action.setActionCode("submit");
        action.setActionType("SUBMIT");
        action.setLabel("Submit");
        action.setPermissionCode("/api/v1/forms/*/submit:POST");
        action.setMetadataJson("{}");
        action.setStatus("ENABLED");
        return action;
    }

    private LcFormDefinition publishedForm() {
        return form("PUBLISHED");
    }

    private LcFormDefinition form(String status) {
        LcFormDefinition form = new LcFormDefinition();
        form.setId("FORM001");
        form.setTenantId("tenant-a");
        form.setFormKey("expense");
        form.setVersion(1);
        form.setStatus(status);
        form.setSchemaHash("hash");
        form.setSchemaJson("{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}");
        return form;
    }

    private void setTenantUser() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", "tenant-a", List.of("ADMIN"), List.of(), null, null, null));
    }
}
