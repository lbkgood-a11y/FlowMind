package com.triobase.service.openapi.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.integration.IntegrationAdmissionDecision;
import com.triobase.common.dto.integration.IntegrationAdmissionRequest;
import com.triobase.service.openapi.domain.enums.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeAdmissionContextResolverTest {

    @Mock private IntegrationAdmissionService admissionService;

    @Test
    void trustedGatewayHeadersBecomeRuntimeContextWithoutReauthenticating() {
        RuntimeAdmissionContextResolver resolver = resolver();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/openapi/runtime/orders.get");
        request.addHeader("X-Gateway-Authenticated", "true");
        request.addHeader("X-OpenAPI-Gateway-Secret", "gateway-secret");
        request.addHeader("X-Tenant-Id", "tenant-a");
        request.addHeader("X-Environment", "PROD");
        request.addHeader("X-Application-Client-Id", "client-1");
        request.addHeader("X-Subscription-Id", "sub-1");
        request.addHeader("X-Policy-Version", "7");
        request.addHeader("X-Max-Concurrency", "9");

        var context = resolver.resolve(request, "orders.get", Environment.PROD, "POST");

        assertThat(context.tenantId()).isEqualTo("tenant-a");
        assertThat(context.applicationClientId()).isEqualTo("client-1");
        assertThat(context.subscriptionId()).isEqualTo("sub-1");
        assertThat(context.policyVersion()).isEqualTo(7L);
        assertThat(context.maxConcurrency()).isEqualTo(9L);
        verifyNoInteractions(admissionService);
    }

    @Test
    void directRuntimeRequestUsesAdmissionService() {
        RuntimeAdmissionContextResolver resolver = resolver();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/openapi/runtime/orders.get");
        request.addHeader("X-Tenant-Id", "tenant-a");
        request.addHeader("X-Client-Key", "client-key");
        request.addHeader("X-Client-Credential", "secret");
        request.addHeader("X-Environment", "TEST");
        when(admissionService.admit(org.mockito.ArgumentMatchers.any())).thenReturn(
                new IntegrationAdmissionDecision(true, 200, "ALLOWED", "tenant-a",
                        "client-1", "sub-1", 8, 0, 1024, 11, 3));

        var context = resolver.resolve(request, "orders.get", Environment.TEST, "GET");

        assertThat(context.applicationClientId()).isEqualTo("client-1");
        assertThat(context.subscriptionId()).isEqualTo("sub-1");
        assertThat(context.maxConcurrency()).isEqualTo(11);
        ArgumentCaptor<IntegrationAdmissionRequest> admission = ArgumentCaptor.forClass(IntegrationAdmissionRequest.class);
        verify(admissionService).admit(admission.capture());
        assertThat(admission.getValue().clientKey()).isEqualTo("client-key");
        assertThat(admission.getValue().credential()).isEqualTo("secret");
        assertThat(admission.getValue().routeKey()).isEqualTo("orders.get");
    }

    @Test
    void deniedDirectAdmissionFailsClosed() {
        RuntimeAdmissionContextResolver resolver = resolver();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/openapi/runtime/orders.get");
        when(admissionService.admit(org.mockito.ArgumentMatchers.any())).thenReturn(
                IntegrationAdmissionDecision.deny(403, "ACCESS_DENIED", 0));

        assertThatThrownBy(() -> resolver.resolve(request, "orders.get", Environment.TEST, "GET"))
                .isInstanceOf(BizException.class)
                .hasMessage("ACCESS_DENIED");
    }

    @Test
    void forgedGatewayHeaderFallsBackToDirectAdmission() {
        RuntimeAdmissionContextResolver resolver = resolver();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/openapi/runtime/orders.get");
        request.addHeader("X-Gateway-Authenticated", "true");
        request.addHeader("X-OpenAPI-Gateway-Secret", "wrong-secret");
        request.addHeader("X-Tenant-Id", "tenant-a");
        request.addHeader("X-Client-Key", "client-key");
        request.addHeader("X-Client-Credential", "secret");
        when(admissionService.admit(org.mockito.ArgumentMatchers.any())).thenReturn(
                new IntegrationAdmissionDecision(true, 200, "ALLOWED", "tenant-a",
                        "client-1", "sub-1", 8, 0, 1024, 11, 3));

        var context = resolver.resolve(request, "orders.get", Environment.TEST, "GET");

        assertThat(context.applicationClientId()).isEqualTo("client-1");
        verify(admissionService).admit(org.mockito.ArgumentMatchers.any());
    }

    private RuntimeAdmissionContextResolver resolver() {
        return new RuntimeAdmissionContextResolver(
                admissionService, new GatewayTrustVerifier("gateway-secret"));
    }
}
