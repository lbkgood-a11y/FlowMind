package com.triobase.service.openapi.integration.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.ConnectorOperationClass;
import com.triobase.service.openapi.integration.credential.OutboundAuthenticationResolver;
import com.triobase.service.openapi.integration.http.JdkOutboundIntegrationClient;
import com.triobase.service.openapi.service.OutboundTargetPolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiOutboundLoadAcceptanceTest {

    @Test
    void handlesBoundedConcurrentReadLoadWithoutLosingRequests() throws Exception {
        AtomicInteger received = new AtomicInteger();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (var serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
             var callerExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(serverExecutor);
            server.createContext("/read", exchange -> {
                received.incrementAndGet();
                byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JdkOutboundIntegrationClient client = new JdkOutboundIntegrationClient(
                        new OutboundTargetPolicy(),
                        new OutboundAuthenticationResolver(material -> "token"),
                        registry, List.of(), null, objectMapper);
                ConnectorVersion connector = new ConnectorVersion();
                connector.setId("pilot-read-v1");
                connector.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
                connector.setOperationPath("/read");
                connector.setHttpMethod("POST");
                connector.setTimeoutMillis(5_000);
                connector.setOperationClass(ConnectorOperationClass.READ_ONLY);
                connector.setAuthenticationType(AuthenticationType.NONE);
                connector.setNetworkPolicy(objectMapper.createObjectNode()
                        .put("allowPrivateNetwork", true));
                connector.setResponseSizeLimit(1024L);

                var futures = IntStream.range(0, 40).mapToObj(index -> callerExecutor.submit(() -> {
                    TraceUtil.setTraceId("load-trace-" + index);
                    try {
                        return client.execute(new OutboundIntegrationClient.OutboundRequest(
                                connector, objectMapper.createObjectNode().put("index", index),
                                Map.of(), null)).status();
                    } finally {
                        TraceUtil.clear();
                    }
                })).toList();

                for (var future : futures) {
                    assertThat(future.get()).isEqualTo(200);
                }
                assertThat(received).hasValue(40);
            } finally {
                server.stop(0);
            }
        } finally {
            registry.close();
        }
    }
}

