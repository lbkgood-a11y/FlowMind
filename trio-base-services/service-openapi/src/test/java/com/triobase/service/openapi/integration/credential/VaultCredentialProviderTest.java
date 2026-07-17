package com.triobase.service.openapi.integration.credential;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VaultCredentialProviderTest {

    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void resolvesKvV2MaterialUsingVaultTokenHeader() {
        AtomicReference<String> token = new AtomicReference<>();
        server.createContext("/v1/secret/data/partner", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Vault-Token"));
            byte[] body = "{\"data\":{\"data\":{\"apiKey\":\"vault-value\"}}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        VaultCredentialProvider provider = new VaultCredentialProvider(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "vault-token");

        CredentialMaterial material = provider.resolve("secret/data/partner");

        assertThat(material.required("apiKey")).isEqualTo("vault-value");
        assertThat(token.get()).isEqualTo("vault-token");
    }
}
