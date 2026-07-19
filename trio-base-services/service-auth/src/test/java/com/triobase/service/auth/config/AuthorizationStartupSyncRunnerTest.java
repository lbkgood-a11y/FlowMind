package com.triobase.service.auth.config;

import com.triobase.common.dto.authz.AuthorizationResourceSyncRequest;
import com.triobase.common.dto.authz.CustomDocumentAuthorizationManifest;
import com.triobase.service.auth.service.AuthorizationRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthorizationStartupSyncRunnerTest {

    @Mock
    private AuthorizationRegistryService registryService;

    @Captor
    private ArgumentCaptor<AuthorizationResourceSyncRequest> requestCaptor;

    private CustomDocumentAuthorizationManifest invoiceManifest;

    @BeforeEach
    void setUp() {
        invoiceManifest = new CustomDocumentAuthorizationManifest();
        invoiceManifest.setTenantId("tenant-a");
        invoiceManifest.setServiceName("service-billing");

        CustomDocumentAuthorizationManifest.Document doc = new CustomDocumentAuthorizationManifest.Document();
        doc.setCode("CUSTOM_DOC:INVOICE");
        doc.setDocumentType("CUSTOM_DOC");
        doc.setDisplayName("Invoice");
        doc.setBusinessObjectId("billing/invoice");

        CustomDocumentAuthorizationManifest.Action view = new CustomDocumentAuthorizationManifest.Action();
        view.setActionCode("VIEW");
        doc.getActions().add(view);

        CustomDocumentAuthorizationManifest.Action approve = new CustomDocumentAuthorizationManifest.Action();
        approve.setActionCode("APPROVE");
        approve.setGuardCodes(List.of("WORKFLOW_CANDIDATE", "NO_SELF_APPROVAL"));
        doc.getActions().add(approve);

        CustomDocumentAuthorizationManifest.Field amount = new CustomDocumentAuthorizationManifest.Field();
        amount.setFieldKey("amount");
        amount.setFieldLabel("Amount");
        amount.setFieldType("number");
        amount.setSensitivityClassification("FINANCIAL");
        amount.setDefaultMaskStrategy("LAST4");
        doc.getFields().add(amount);

        CustomDocumentAuthorizationManifest.Guard guard = new CustomDocumentAuthorizationManifest.Guard();
        guard.setGuardCode("DOCUMENT_STATUS");
        guard.setOwnerService("service-billing");
        guard.setSupportedResourceTypes("CUSTOM_DOC");
        guard.setDescription("文档状态必须允许当前操作");
        doc.getGuards().add(guard);

        invoiceManifest.getDocuments().add(doc);
    }

    @Test
    void startupSyncPersistsManifestResources() {
        AuthorizationStartupSyncRunner runner = new AuthorizationStartupSyncRunner(
                List.of(invoiceManifest), registryService);

        runner.syncOnStartup();

        verify(registryService).synchronize(requestCaptor.capture());
        AuthorizationResourceSyncRequest request = requestCaptor.getValue();
        assertThat(request.getTenantId()).isEqualTo("tenant-a");
        assertThat(request.getOwnerService()).isEqualTo("service-billing");
        assertThat(request.getResources()).hasSize(1);
        assertThat(request.getResources().get(0).getResourceCode()).isEqualTo("CUSTOM_DOC:INVOICE");
        assertThat(request.getResources().get(0).getActions()).hasSize(2);
        assertThat(request.getResources().get(0).getFields()).hasSize(1);
        assertThat(request.getResources().get(0).getGuards()).hasSize(1);
    }

    @Test
    void skipsWhenNoManifests() {
        AuthorizationStartupSyncRunner runner = new AuthorizationStartupSyncRunner(
                List.of(), registryService);

        runner.syncOnStartup();

        verifyNoInteractions(registryService);
    }

    @Test
    void skipsNullServiceName() {
        invoiceManifest.setServiceName(null);
        AuthorizationStartupSyncRunner runner = new AuthorizationStartupSyncRunner(
                List.of(invoiceManifest), registryService);

        runner.syncOnStartup();

        verifyNoInteractions(registryService);
    }

    @Test
    void skipsDocumentWithoutCode() {
        invoiceManifest.getDocuments().get(0).setCode(null);
        AuthorizationStartupSyncRunner runner = new AuthorizationStartupSyncRunner(
                List.of(invoiceManifest), registryService);

        runner.syncOnStartup();

        verifyNoInteractions(registryService);
    }

    @Test
    void syncFailureLoggedButDoesNotThrow() {
        doThrow(new RuntimeException("DB unavailable"))
                .when(registryService).synchronize(any());
        AuthorizationStartupSyncRunner runner = new AuthorizationStartupSyncRunner(
                List.of(invoiceManifest), registryService);

        // Must not propagate the exception
        runner.syncOnStartup();

        verify(registryService).synchronize(any());
    }

    @Test
    void multipleDocumentsInOneManifestProducesMultipleResources() {
        CustomDocumentAuthorizationManifest.Document doc2 = new CustomDocumentAuthorizationManifest.Document();
        doc2.setCode("CUSTOM_DOC:CREDIT_NOTE");
        doc2.setDisplayName("Credit Note");
        invoiceManifest.getDocuments().add(doc2);

        AuthorizationStartupSyncRunner runner = new AuthorizationStartupSyncRunner(
                List.of(invoiceManifest), registryService);

        runner.syncOnStartup();

        verify(registryService).synchronize(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getResources()).hasSize(2);
    }

    @Test
    void manifestFromExampleConfigConstructorHasCorrectShape() {
        ExampleDocumentAuthorizationConfig config = new ExampleDocumentAuthorizationConfig();

        CustomDocumentAuthorizationManifest manifest = config.referenceContractManifest();

        assertThat(manifest.getServiceName()).isEqualTo("service-openapi");
        assertThat(manifest.getDocuments()).hasSize(1);
        CustomDocumentAuthorizationManifest.Document doc = manifest.getDocuments().get(0);
        assertThat(doc.getCode()).isEqualTo("CUSTOM_DOC:CONTRACT");
        assertThat(doc.getActions()).hasSize(6);
        assertThat(doc.getFields()).hasSize(3);
        assertThat(doc.getGuards()).hasSize(2);

        // Verify the guarantee actions carry guard codes
        CustomDocumentAuthorizationManifest.Action approve = doc.getActions().stream()
                .filter(a -> "APPROVE".equals(a.getActionCode()))
                .findFirst().orElseThrow();
        assertThat(approve.getGuardCodes()).contains("WORKFLOW_CANDIDATE", "NO_SELF_APPROVAL");
    }
}
