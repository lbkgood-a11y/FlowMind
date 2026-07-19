package com.triobase.service.openapi.service.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestCustomDocumentDecisionClientTest {

    @Test
    void decidePostsInternalDecisionRequestAndMapsEnvelopeData() {
        TestClient testClient = client();
        testClient.server.expect(requestTo("http://auth.test/internal/v1/authz/decide"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, "service-openapi"))
                .andExpect(header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, "sync-token"))
                .andExpect(jsonPath("$.resourceCode").value("CUSTOM_DOC:CONTRACT"))
                .andExpect(jsonPath("$.actionCode").value("VIEW"))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"allowed":true,"resourceCode":"CUSTOM_DOC:CONTRACT","actionCode":"VIEW"}}
                        """, MediaType.APPLICATION_JSON));

        var response = testClient.client.decide(request());

        assertTrue(response.isAllowed());
        assertEquals("CUSTOM_DOC:CONTRACT", response.getResourceCode());
        assertEquals("VIEW", response.getActionCode());
    }

    @Test
    void decideThrowsWhenAuthEnvelopeFails() {
        TestClient testClient = client();
        testClient.server.expect(requestTo("http://auth.test/internal/v1/authz/decide"))
                .andRespond(withSuccess("{\"code\":500}", MediaType.APPLICATION_JSON));

        BizException exception = assertThrows(BizException.class, () -> testClient.client.decide(request()));

        assertEquals("CUSTOM_DOC_AUTHZ_DECISION_FAILED", exception.getMessage());
    }

    private AuthorizationDecisionRequest request() {
        AuthorizationDecisionRequest request = new AuthorizationDecisionRequest();
        request.setTenantId("tenant-a");
        request.setUserId("user-1");
        request.setResourceCode("CUSTOM_DOC:CONTRACT");
        request.setActionCode("VIEW");
        request.setOwnerService("service-openapi");
        request.setEnforcementMode(true);
        return request;
    }

    private TestClient client() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InternalServiceSecurityProperties properties = new InternalServiceSecurityProperties();
        properties.setToken("sync-token");
        return new TestClient(new RestCustomDocumentDecisionClient(
                properties, builder.build(), new ObjectMapper()), server);
    }

    private record TestClient(RestCustomDocumentDecisionClient client, MockRestServiceServer server) {
    }
}
