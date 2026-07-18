package com.triobase.service.openapi.controller;

import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.CallbackAcknowledgement;
import com.triobase.service.openapi.service.CallbackRuntimeService;
import com.triobase.service.openapi.service.GatewayTrustVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallbackRuntimeControllerTest {

    @Mock private CallbackRuntimeService service;

    @Test
    void spoofedGatewayHeaderIsNotTrustedWithoutSharedSecret() {
        CallbackRuntimeController controller = new CallbackRuntimeController(
                service, new GatewayTrustVerifier("gateway-secret"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/openapi/callbacks/cb_key");
        request.addHeader("X-Gateway-Authenticated", "true");
        when(service.receive(eq("cb_key"), eq("tenant-a"), eq(Environment.TEST), eq("client-1"),
                any(), any())).thenReturn(ack());

        controller.receive("cb_key", "tenant-a", Environment.TEST, "client-1",
                "ts", "nonce", "sig", "hash", "{}".getBytes(), request);

        ArgumentCaptor<CallbackRuntimeService.CallbackHeaders> headers =
                ArgumentCaptor.forClass(CallbackRuntimeService.CallbackHeaders.class);
        verify(service).receive(eq("cb_key"), eq("tenant-a"), eq(Environment.TEST), eq("client-1"),
                any(), headers.capture());
        assertThat(headers.getValue().gatewayAuthenticated()).isFalse();
    }

    @Test
    void sharedSecretMarksCallbackAsGatewayAuthenticated() {
        CallbackRuntimeController controller = new CallbackRuntimeController(
                service, new GatewayTrustVerifier("gateway-secret"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/openapi/callbacks/cb_key");
        request.addHeader("X-Gateway-Authenticated", "true");
        request.addHeader("X-OpenAPI-Gateway-Secret", "gateway-secret");
        when(service.receive(eq("cb_key"), eq("tenant-a"), eq(Environment.TEST), eq("client-1"),
                any(), any())).thenReturn(ack());

        controller.receive("cb_key", "tenant-a", Environment.TEST, "client-1",
                "ts", "nonce", "sig", "hash", "{}".getBytes(), request);

        ArgumentCaptor<CallbackRuntimeService.CallbackHeaders> headers =
                ArgumentCaptor.forClass(CallbackRuntimeService.CallbackHeaders.class);
        verify(service).receive(eq("cb_key"), eq("tenant-a"), eq(Environment.TEST), eq("client-1"),
                any(), headers.capture());
        assertThat(headers.getValue().gatewayAuthenticated()).isTrue();
    }

    private CallbackAcknowledgement ack() {
        return new CallbackAcknowledgement("inbox-1", 202, "application/json",
                "{\"accepted\":true}", false, false);
    }
}
