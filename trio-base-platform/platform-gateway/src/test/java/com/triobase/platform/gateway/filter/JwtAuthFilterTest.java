package com.triobase.platform.gateway.filter;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void propagatesTenantRolesPermissionsAndVersionHeaders() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/validate", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            byte[] body = """
                    {"code":0,"message":"success","data":{"valid":true,
                    "userId":"U001","username":"alice","tenantId":"tenant-a",
                    "roles":["ADMIN","USER"],
                    "permissions":["/api/v1/forms:GET","/api/v1/forms/*/instances:GET"],
                    "authVersion":3,"roleVersion":4,"dataPolicyVersion":5}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        JwtAuthFilter filter = new JwtAuthFilter(WebClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "/api/v1/auth/**");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/forms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .build());
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();

        filter.filter(exchange, e -> {
            forwarded.set(e.getRequest().getHeaders());
            return Mono.empty();
        }).block();

        assertThat(query.get()).contains("token=access-token");
        assertThat(forwarded.get().getFirst("X-User-Id")).isEqualTo("U001");
        assertThat(forwarded.get().getFirst("X-Username")).isEqualTo("alice");
        assertThat(forwarded.get().getFirst("X-Tenant-Id")).isEqualTo("tenant-a");
        assertThat(forwarded.get().getFirst("X-User-Roles")).isEqualTo("ADMIN,USER");
        assertThat(forwarded.get().getFirst("X-User-Permissions"))
                .isEqualTo("/api/v1/forms:GET,/api/v1/forms/*/instances:GET");
        assertThat(forwarded.get().getFirst("X-Auth-Version")).isEqualTo("3");
        assertThat(forwarded.get().getFirst("X-Role-Version")).isEqualTo("4");
        assertThat(forwarded.get().getFirst("X-Data-Policy-Version")).isEqualTo("5");
    }
}
