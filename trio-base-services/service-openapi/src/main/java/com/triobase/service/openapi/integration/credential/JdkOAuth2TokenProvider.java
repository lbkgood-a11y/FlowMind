package com.triobase.service.openapi.integration.credential;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.service.OutboundTargetPolicy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class JdkOAuth2TokenProvider implements OAuth2TokenProvider {

    private final ObjectMapper objectMapper;
    private final OutboundTargetPolicy targetPolicy;
    private final HttpClient httpClient;

    public JdkOAuth2TokenProvider(ObjectMapper objectMapper, OutboundTargetPolicy targetPolicy) {
        this.objectMapper = objectMapper;
        this.targetPolicy = targetPolicy;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String clientCredentialsToken(CredentialMaterial material) {
        String endpoint = material.required("tokenEndpoint");
        targetPolicy.validate(endpoint, null);
        List<String> fields = new ArrayList<>();
        fields.add(form("grant_type", "client_credentials"));
        fields.add(form("client_id", material.required("clientId")));
        fields.add(form("client_secret", material.required("clientSecret")));
        addOptional(fields, "scope", material.values().get("scope"));
        addOptional(fields, "audience", material.values().get("audience"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(String.join("&", fields)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(50231, "OPENAPI_OAUTH2_TOKEN_REQUEST_FAILED");
            }
            JsonNode payload = objectMapper.readTree(response.body());
            String token = payload.path("access_token").asText();
            if (token.isBlank()) {
                throw new BizException(50231, "OPENAPI_OAUTH2_TOKEN_RESPONSE_INVALID");
            }
            return token;
        } catch (BizException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException(50231, "OPENAPI_OAUTH2_TOKEN_REQUEST_FAILED");
        } catch (Exception exception) {
            throw new BizException(50231, "OPENAPI_OAUTH2_TOKEN_REQUEST_FAILED");
        }
    }

    private void addOptional(List<String> fields, String name, String value) {
        if (value != null && !value.isBlank()) {
            fields.add(form(name, value));
        }
    }

    private String form(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
