package com.triobase.service.openapi.service.authorization;

import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CustomDocumentAuthorizationSyncClientTest {

    @Test
    void syncReferenceContractManifestPostsCustomDocumentResource() {
        TestClient testClient = client();
        testClient.server.expect(requestTo("http://auth.test/internal/v1/authz/resources/sync"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, "service-openapi"))
                .andExpect(header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, "sync-token"))
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.ownerService").value("service-openapi"))
                .andExpect(jsonPath("$.resources[0].resourceCode").value("CUSTOM_DOC:CONTRACT"))
                .andExpect(jsonPath("$.resources[0].resourceType").value("CUSTOM_DOC"))
                .andExpect(jsonPath("$.resources[0].actions[*].actionCode",
                        hasItems("VIEW", "CREATE", "EDIT", "SUBMIT", "APPROVE", "EXPORT")))
                .andExpect(jsonPath("$.resources[0].actions[?(@.actionCode == 'APPROVE')].guardCodes[*]",
                        hasItems("WORKFLOW_CANDIDATE", "NO_SELF_APPROVAL", "DOCUMENT_STATUS")))
                .andExpect(jsonPath("$.resources[0].fields[*].fieldKey",
                        hasItems("amount", "customerName", "paymentTerms")))
                .andExpect(jsonPath("$.resources[0].guards[*].guardCode",
                        hasItems("DOCUMENT_STATUS", "ARCHIVED_LOCK", "WORKFLOW_CANDIDATE", "NO_SELF_APPROVAL")))
                .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));

        testClient.client.sync(ReferenceContractAuthorizationManifest.create("tenant-a"));

        testClient.server.verify();
    }

    @Test
    void syncRejectsInvalidCustomDocumentCode() {
        CustomDocumentAuthorizationSyncClient client = client().client;
        var manifest = ReferenceContractAuthorizationManifest.create("tenant-a");
        manifest.getDocuments().getFirst().setCode("CONTRACT");

        BizException exception = assertThrows(BizException.class, () -> client.toSyncRequest(manifest));

        assertEquals("CUSTOM_DOC_AUTHZ_RESOURCE_CODE_INVALID", exception.getMessage());
    }

    private TestClient client() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InternalServiceSecurityProperties properties = new InternalServiceSecurityProperties();
        properties.setToken("sync-token");
        return new TestClient(new CustomDocumentAuthorizationSyncClient(properties, builder.build()), server);
    }

    private record TestClient(CustomDocumentAuthorizationSyncClient client, MockRestServiceServer server) {
    }
}
