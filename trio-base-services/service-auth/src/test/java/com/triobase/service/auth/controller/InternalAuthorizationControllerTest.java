package com.triobase.service.auth.controller;

import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionResponse;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.service.auth.dto.AuthorizationSyncResponse;
import com.triobase.service.auth.service.AuthorizationDecisionService;
import com.triobase.service.auth.service.AuthorizationRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalAuthorizationControllerTest {

    private AuthorizationDecisionService decisionService;
    private AuthorizationRegistryService registryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        decisionService = mock(AuthorizationDecisionService.class);
        registryService = mock(AuthorizationRegistryService.class);
        InternalServiceSecurityProperties properties = new InternalServiceSecurityProperties();
        properties.setToken("test-token");
        properties.setAllowedCallers(List.of("service-lowcode"));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalAuthorizationController(decisionService, registryService))
                .addFilters(new InternalServiceTokenFilter(properties))
                .build();
    }

    @Test
    void rejectsMissingInternalCredentials() throws Exception {
        mockMvc.perform(post("/internal/v1/authz/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void decideAcceptsTrustedInternalCaller() throws Exception {
        AuthorizationDecisionResponse response = decision("LOWCODE_FORM:EXPENSE", "VIEW", true);
        when(decisionService.decide(any())).thenReturn(response);

        mockMvc.perform(post("/internal/v1/authz/decide")
                        .headers(internalHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "userId": "U001",
                                  "resourceCode": "LOWCODE_FORM:EXPENSE",
                                  "actionCode": "VIEW"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.resourceCode").value("LOWCODE_FORM:EXPENSE"));

        ArgumentCaptor<AuthorizationDecisionRequest> captor =
                ArgumentCaptor.forClass(AuthorizationDecisionRequest.class);
        verify(decisionService).decide(captor.capture());
        assertEquals("tenant-a", captor.getValue().getTenantId());
        assertEquals("VIEW", captor.getValue().getActionCode());
    }

    @Test
    void batchDecideReturnsStableDecisionList() throws Exception {
        AuthorizationBatchDecisionResponse response = new AuthorizationBatchDecisionResponse();
        response.setDecisions(List.of(
                decision("LOWCODE_FORM:EXPENSE", "VIEW", true),
                decision("LOWCODE_FORM:EXPENSE", "EXPORT", false)));
        when(decisionService.batchDecide(any())).thenReturn(response);

        mockMvc.perform(post("/internal/v1/authz/batch-decide")
                        .headers(internalHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decisions": [
                                    {"resourceCode": "LOWCODE_FORM:EXPENSE", "actionCode": "VIEW"},
                                    {"resourceCode": "LOWCODE_FORM:EXPENSE", "actionCode": "EXPORT"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decisions[0].allowed").value(true))
                .andExpect(jsonPath("$.data.decisions[1].allowed").value(false));
    }

    @Test
    void resourceSyncAcceptsTrustedInternalCaller() throws Exception {
        AuthorizationSyncResponse response = new AuthorizationSyncResponse();
        response.setTenantId("tenant-a");
        response.setOwnerService("service-lowcode");
        response.setResourceCount(1);
        response.setResourceCodes(List.of("LOWCODE_FORM:EXPENSE"));
        when(registryService.synchronize(any())).thenReturn(response);

        mockMvc.perform(post("/internal/v1/authz/resources/sync")
                        .headers(internalHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "ownerService": "service-lowcode",
                                  "resources": [
                                    {"resourceCode": "LOWCODE_FORM:EXPENSE", "resourceType": "LOWCODE_FORM"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceCount").value(1))
                .andExpect(jsonPath("$.data.resourceCodes[0]").value("LOWCODE_FORM:EXPENSE"));
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(InternalServiceTokenFilter.HEADER_SERVICE_NAME, "service-lowcode");
        headers.add(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, "test-token");
        return headers;
    }

    private AuthorizationDecisionResponse decision(String resourceCode, String actionCode, boolean allowed) {
        AuthorizationDecisionResponse response = new AuthorizationDecisionResponse();
        response.setAllowed(allowed);
        response.setTenantId("tenant-a");
        response.setUserId("U001");
        response.setResourceCode(resourceCode);
        response.setActionCode(actionCode);
        return response;
    }
}
