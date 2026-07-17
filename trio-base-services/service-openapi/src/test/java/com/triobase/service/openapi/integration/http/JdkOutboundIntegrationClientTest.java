package com.triobase.service.openapi.integration.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.ConnectorOperationClass;
import com.triobase.service.openapi.integration.credential.OutboundAuthenticationResolver;
import com.triobase.service.openapi.service.OutboundTargetPolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdkOutboundIntegrationClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private SimpleMeterRegistry meterRegistry;
    private JdkOutboundIntegrationClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        meterRegistry = new SimpleMeterRegistry();
        client = new JdkOutboundIntegrationClient(
                new OutboundTargetPolicy(),
                new OutboundAuthenticationResolver(material -> "token"),
                meterRegistry, List.of(), null, objectMapper);
    }

    @AfterEach
    void tearDown() {
        TraceUtil.clear();
        server.stop(0);
        meterRegistry.close();
    }

    @Test
    void propagatesTraceAndRecordsBoundedResponse() throws Exception {
        AtomicReference<String> trace = new AtomicReference<>();
        server.createContext("/invoke", exchange -> {
            trace.set(exchange.getRequestHeaders().getFirst(TraceUtil.TRACE_ID_KEY));
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        TraceUtil.setTraceId("trace-openapi-001");

        var response = client.execute(new OutboundIntegrationClient.OutboundRequest(
                connector("/invoke", 1024), objectMapper.readTree("{\"id\":1}"), Map.of(), null));

        assertThat(response.status()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("true");
        assertThat(trace.get()).isEqualTo("trace-openapi-001");
        assertThat(meterRegistry.find("triobase.openapi.outbound.duration").timers()).isNotEmpty();
    }

    @Test
    void neverFollowsPartnerRedirects() {
        AtomicInteger redirectedCalls = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl() + "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirected", exchange -> {
            redirectedCalls.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        var response = client.execute(new OutboundIntegrationClient.OutboundRequest(
                connector("/redirect", 1024), null, Map.of(), null));

        assertThat(response.status()).isEqualTo(302);
        assertThat(redirectedCalls).hasValue(0);
    }

    @Test
    void rejectsResponsesAbovePublishedLimit() {
        server.createContext("/large", exchange -> {
            byte[] response = "larger-than-five".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        assertThatThrownBy(() -> client.execute(new OutboundIntegrationClient.OutboundRequest(
                connector("/large", 5), null, Map.of(), null)))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_OUTBOUND_RESPONSE_TOO_LARGE");
    }

    @Test
    void invokesControlledEgressHookBeforeNetworkCall() {
        JdkOutboundIntegrationClient deniedClient = new JdkOutboundIntegrationClient(
                new OutboundTargetPolicy(), new OutboundAuthenticationResolver(material -> "token"),
                meterRegistry, List.of((connector, uri) -> {
                    throw new BizException(40330, "OPENAPI_EGRESS_POLICY_DENIED");
                }), null, objectMapper);

        assertThatThrownBy(() -> deniedClient.execute(new OutboundIntegrationClient.OutboundRequest(
                connector("/never", 1024), null, Map.of(), null)))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_EGRESS_POLICY_DENIED");
    }

    @Test
    void enforcesPublishedConnectorTimeout() {
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(200);
                exchange.sendResponseHeaders(200, -1);
            } catch (Exception ignored) {
                // The client is expected to close the timed-out exchange.
            } finally {
                exchange.close();
            }
        });
        ConnectorVersion slow = connector("/slow", 1024);
        slow.setTimeoutMillis(50);

        assertThatThrownBy(() -> client.execute(new OutboundIntegrationClient.OutboundRequest(
                slow, null, Map.of(), null)))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_OUTBOUND_CALL_FAILED");
    }

    private ConnectorVersion connector(String path, long limit) {
        ConnectorVersion connector = new ConnectorVersion();
        connector.setId("connector-v1");
        connector.setBaseUrl(baseUrl());
        connector.setOperationPath(path);
        connector.setHttpMethod("POST");
        connector.setTimeoutMillis(5_000);
        connector.setOperationClass(ConnectorOperationClass.READ_ONLY);
        connector.setAuthenticationType(AuthenticationType.NONE);
        connector.setNetworkPolicy(objectMapper.createObjectNode().put("allowPrivateNetwork", true));
        connector.setResponseSizeLimit(limit);
        return connector;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
