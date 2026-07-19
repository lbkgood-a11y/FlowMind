package com.triobase.service.lowcode.service;

import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.service.lowcode.dto.ApplicationActionRequest;
import com.triobase.service.lowcode.dto.ApplicationPageRequest;
import com.triobase.service.lowcode.dto.FormFieldSchemaRequest;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthorizationResourceSyncClientTest {

    @Test
    void syncPublishedFormPostsStableResourcePayloadAndCanRetryIdempotently() {
        TestClient testClient = client();
        testClient.server.expect(ExpectedCount.twice(), requestTo("http://auth.test/internal/v1/authz/resources/sync"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, "service-lowcode"))
                .andExpect(header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, "sync-token"))
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.ownerService").value("service-lowcode"))
                .andExpect(jsonPath("$.resources[0].resourceCode").value("LOWCODE_FORM:EXPENSE"))
                .andExpect(jsonPath("$.resources[0].resourceType").value("LOWCODE_FORM"))
                .andExpect(jsonPath("$.resources[0].businessObjectId").value("FORM001"))
                .andExpect(jsonPath("$.resources[0].actions[*].actionCode",
                        hasItems("VIEW", "CREATE", "EDIT", "DELETE", "SUBMIT", "APPROVE", "EXPORT",
                                "FIELD_READ", "FIELD_WRITE")))
                .andExpect(jsonPath("$.resources[0].fields[0].fieldKey").value("amount"))
                .andExpect(jsonPath("$.resources[0].fields[0].sensitivityClassification").value("FINANCIAL"))
                .andExpect(jsonPath("$.resources[0].fields[0].defaultMaskStrategy").value("LAST4"))
                .andExpect(jsonPath("$.resources[0].guards[*].guardCode",
                        hasItems("WORKFLOW_CANDIDATE", "NO_SELF_APPROVAL", "DOCUMENT_STATUS", "ARCHIVED_LOCK")))
                .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));

        testClient.client.syncPublishedForm(form(), List.of(field()));
        testClient.client.syncPublishedForm(form(), List.of(field()));

        testClient.server.verify();
    }

    @Test
    void syncPublishedApplicationPostsApplicationResource() {
        TestClient testClient = client();
        testClient.server.expect(requestTo("http://auth.test/internal/v1/authz/resources/sync"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.ownerService").value("service-lowcode"))
                .andExpect(jsonPath("$.resources[0].resourceCode").value("LOWCODE_APP:EXPENSE_APP"))
                .andExpect(jsonPath("$.resources[0].resourceType").value("LOWCODE_APP"))
                .andExpect(jsonPath("$.resources[0].businessObjectId").value("APPV001"))
                .andExpect(jsonPath("$.resources[0].actions[*].actionCode",
                        hasItems("VIEW", "DESIGN", "PUBLISH", "OFFLINE")))
                .andExpect(jsonPath("$.resources[0].metadataJson").value(
                        "{\"appKey\":\"expense_app\",\"version\":1,\"formKey\":\"expense\",\"pageCount\":1,\"actionCount\":1}"))
                .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));

        testClient.client.syncPublishedApplication(appVersion(), List.of(page()), List.of(action()));

        testClient.server.verify();
    }

    @Test
    void syncOfflineApplicationPostsInactiveLifecycleStatus() {
        TestClient testClient = client();
        testClient.server.expect(requestTo("http://auth.test/internal/v1/authz/resources/sync"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.resources[0].lifecycleStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.resources[0].resourceCode").value("LOWCODE_APP:EXPENSE_APP"))
                .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));

        testClient.client.syncOfflineApplication(appVersion());

        testClient.server.verify();
    }

    @Test
    void syncOfflineApplicationReturnsEarlyOnMissingTenantId() {
        TestClient testClient = client();
        LcApplicationVersion version = appVersion();
        version.setTenantId(null);

        testClient.client.syncOfflineApplication(version);

        testClient.server.verify();
    }

    @Test
    void syncOfflineApplicationReturnsEarlyOnMissingAppKey() {
        TestClient testClient = client();
        LcApplicationVersion version = appVersion();
        version.setAppKey(null);

        testClient.client.syncOfflineApplication(version);

        testClient.server.verify();
    }

    @Test
    void syncOfflineApplicationReturnsEarlyOnNullVersion() {
        TestClient testClient = client();

        testClient.client.syncOfflineApplication(null);

        testClient.server.verify();
    }

    @Test
    void syncFailureResponseThrowsBizException() {
        TestClient testClient = client();
        testClient.server.expect(requestTo("http://auth.test/internal/v1/authz/resources/sync"))
                .andRespond(withSuccess("{\"code\":500}", MediaType.APPLICATION_JSON));

        BizException exception = assertThrows(BizException.class,
                () -> testClient.client.syncPublishedForm(form(), List.of(field())));

        assertEquals("LOWCODE_AUTHZ_SYNC_FAILED", exception.getMessage());
    }

    private TestClient client() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InternalServiceSecurityProperties properties = new InternalServiceSecurityProperties();
        properties.setToken("sync-token");
        return new TestClient(new AuthorizationResourceSyncClient(properties, builder.build()), server);
    }

    private LcFormDefinition form() {
        LcFormDefinition definition = new LcFormDefinition();
        definition.setId("FORM001");
        definition.setTenantId("tenant-a");
        definition.setFormKey("expense");
        definition.setName("Expense");
        definition.setVersion(1);
        return definition;
    }

    private FormFieldSchemaRequest field() {
        FormFieldSchemaRequest field = new FormFieldSchemaRequest();
        field.setFieldKey("amount");
        field.setLabel("Amount");
        field.setFieldType("number");
        field.setSensitivityClassification("FINANCIAL");
        field.setDefaultMaskStrategy("LAST4");
        return field;
    }

    private LcApplicationVersion appVersion() {
        LcApplicationVersion version = new LcApplicationVersion();
        version.setId("APPV001");
        version.setTenantId("tenant-a");
        version.setAppKey("expense_app");
        version.setName("Expense App");
        version.setVersion(1);
        version.setFormKey("expense");
        return version;
    }

    private ApplicationPageRequest page() {
        ApplicationPageRequest page = new ApplicationPageRequest();
        page.setPageType("LIST");
        page.setMetadataJson("{\"columns\":[{\"fieldKey\":\"amount\"}]}");
        return page;
    }

    private ApplicationActionRequest action() {
        ApplicationActionRequest action = new ApplicationActionRequest();
        action.setActionCode("submit");
        action.setActionType("SUBMIT");
        action.setLabel("Submit");
        return action;
    }

    private record TestClient(AuthorizationResourceSyncClient client, MockRestServiceServer server) {
    }
}
